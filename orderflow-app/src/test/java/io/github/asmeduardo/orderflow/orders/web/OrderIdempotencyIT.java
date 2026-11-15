package io.github.asmeduardo.orderflow.orders.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class OrderIdempotencyIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ProductService products;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void retryWithTheSameKeyAndPayloadReturnsTheOriginalOrder() {
        products.register("IDEM-A", "Item", 10);
        var key = UUID.randomUUID().toString();
        var body = order("IDEM-A", 3);

        var first = place(key, body);
        var retry = place(key, body);

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(retry).hasStatus(HttpStatus.CREATED).headers().hasValue("Idempotent-Replayed", "true");
        assertThat(idOf(retry)).isEqualTo(idOf(first));
        assertThat(retry.getResponse().getHeader("Location")).isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(products.get("IDEM-A").reservedQuantity()).isEqualTo(3);
    }

    @Test
    void reusingAKeyForADifferentPayloadIsRejected() {
        products.register("IDEM-B", "Item", 10);
        var key = UUID.randomUUID().toString();
        place(key, order("IDEM-B", 1));

        var reuse = place(key, order("IDEM-B", 2));

        assertThat(reuse)
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:orderflow:problem:idempotency-key-reused");
        assertThat(products.get("IDEM-B").reservedQuantity()).isEqualTo(1);
    }

    @Test
    void keyIsRequiredAndMustBeWellFormed() {
        var missing = mvc.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(order("IDEM-C", 1));

        assertThat(missing)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:orderflow:problem:invalid-request");
        assertThat(place("short", order("IDEM-C", 1)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("Idempotency-Key");
    }

    @Test
    void concurrentRetriesCreateExactlyOneOrder() throws Exception {
        products.register("IDEM-RACE", "Item", 100);
        var key = UUID.randomUUID().toString();
        var body = order("IDEM-RACE", 2);
        int clients = 12;
        var start = new CountDownLatch(1);

        var ids = new ArrayList<String>();
        try (var executor = Executors.newFixedThreadPool(clients)) {
            var futures = new ArrayList<Future<MvcTestResult>>();
            for (int i = 0; i < clients; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return place(key, body);
                }));
            }
            start.countDown();
            for (var future : futures) {
                var result = future.get(60, TimeUnit.SECONDS);
                assertThat(result).hasStatus(HttpStatus.CREATED);
                ids.add(idOf(result));
            }
        }

        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(products.get("IDEM-RACE").reservedQuantity()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from orders.sales_order_item where sku = 'IDEM-RACE'", Integer.class))
                .isEqualTo(1);
    }

    private MvcTestResult place(String key, String body) {
        return mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String order(String sku, int quantity) {
        return """
                {"channel": "WEB_STORE", "currency": "BRL",
                 "items": [{"sku": "%s", "quantity": %d, "unitPrice": 25.00}]}
                """
                .formatted(sku, quantity);
    }

    private static String idOf(MvcTestResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}

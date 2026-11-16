package io.github.asmeduardo.orderflow.orders.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class OrderCancellationIT {

    private static final Duration EVENTUALLY = Duration.ofSeconds(30);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ProductService products;

    @Test
    void pendingOrderCanBeCancelledBeforeInventoryAnswers() throws Exception {
        products.register("CNL-A", "Item", 4);
        var orderId = place("CNL-A", 3);

        var cancelled = mvc.post().uri("/api/v1/orders/{id}/cancel", orderId).exchange();

        assertThat(cancelled)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CANCELLED");
        // Whatever the inventory reserved for the order is released by the OrderCancelled event.
        Awaitility.await().atMost(EVENTUALLY).until(() -> products.get("CNL-A").availableQuantity() == 4);
        assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CANCELLED");
    }

    @Test
    void cancellingTwiceIsHarmless() throws Exception {
        products.register("CNL-B", "Item", 4);
        var orderId = place("CNL-B", 2);
        mvc.post().uri("/api/v1/orders/{id}/cancel", orderId).exchange();

        assertThat(mvc.post().uri("/api/v1/orders/{id}/cancel", orderId))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CANCELLED");
        Awaitility.await().atMost(EVENTUALLY).until(() -> products.get("CNL-B").availableQuantity() == 4);
    }

    @Test
    void rejectedOrderCannotBeCancelled() throws Exception {
        products.register("CNL-C", "Item", 1);
        var orderId = place("CNL-C", 2);
        Awaitility.await().atMost(EVENTUALLY).until(() -> "REJECTED".equals(statusOf(orderId)));

        assertThat(mvc.post().uri("/api/v1/orders/{id}/cancel", orderId))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:orderflow:problem:invalid-order-state");
    }

    @Test
    void cancellingAnUnknownOrderIsNotFound() {
        assertThat(mvc.post().uri("/api/v1/orders/{id}/cancel", UUID.randomUUID()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private String statusOf(String orderId) throws UnsupportedEncodingException {
        return JsonPath.read(
                mvc.get()
                        .uri("/api/v1/orders/{id}", orderId)
                        .exchange()
                        .getResponse()
                        .getContentAsString(),
                "$.status");
    }

    private String place(String sku, int quantity) throws UnsupportedEncodingException {
        var body = """
                {"channel": "WEB_STORE", "currency": "BRL",
                 "items": [{"sku": "%s", "quantity": %d, "unitPrice": 10.00}]}
                """.formatted(sku, quantity);
        var result = mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}

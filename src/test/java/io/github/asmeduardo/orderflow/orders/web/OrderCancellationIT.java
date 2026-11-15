package io.github.asmeduardo.orderflow.orders.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class OrderCancellationIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ProductService products;

    @Test
    void cancellingAConfirmedOrderReleasesItsStock() throws Exception {
        products.register("CNL-A", "Item", 4);
        var orderId = place("CNL-A", 3);

        var cancelled = mvc.post().uri("/api/v1/orders/{id}/cancel", orderId).exchange();

        assertThat(cancelled).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(products.get("CNL-A").reservedQuantity()).isZero();
        assertThat(products.get("CNL-A").availableQuantity()).isEqualTo(4);
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
        assertThat(products.get("CNL-B").availableQuantity()).isEqualTo(4);
    }

    @Test
    void rejectedOrderCannotBeCancelled() throws Exception {
        products.register("CNL-C", "Item", 1);
        var orderId = place("CNL-C", 2);

        assertThat(mvc.post().uri("/api/v1/orders/{id}/cancel", orderId))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:orderflow:problem:invalid-order-state");
    }

    @Test
    void cancellingAnUnknownOrderIsNotFound() {
        assertThat(mvc.post().uri("/api/v1/orders/{id}/cancel", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    }

    private String place(String sku, int quantity) throws UnsupportedEncodingException {
        var body = """
                {"channel": "WEB_STORE", "currency": "BRL",
                 "items": [{"sku": "%s", "quantity": %d, "unitPrice": 10.00}]}
                """
                .formatted(sku, quantity);
        var result = mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}

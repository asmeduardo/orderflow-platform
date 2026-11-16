package io.github.asmeduardo.orderflow.orders.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class OrderApiIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void placedOrderIsRecordedAsPendingWithExactTotals() {
        var placed = place("""
                {"channel": "WEB_STORE", "currency": "BRL",
                 "items": [{"sku": "ORD-MUG", "quantity": 2, "unitPrice": 59.9}]}
                """);

        assertThat(placed).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
                        {"status": "PENDING", "channel": "WEB_STORE", "currency": "BRL",
                         "items": [{"sku": "ORD-MUG", "quantity": 2, "unitPrice": 59.90, "lineTotal": 119.80}],
                         "totalAmount": 119.80}
                        """);
        assertThat(placed).headers().containsHeader("Location");
        assertThat(mvc.get().uri(placed.getResponse().getHeader("Location")))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalAmount")
                .isEqualTo(119.80);
    }

    @Test
    void invalidOrderIsRejectedWithFieldErrors() {
        var response = place("""
                {"channel": "", "currency": "brl",
                 "items": [{"sku": "ok-not", "quantity": 0, "unitPrice": -1}]}
                """);

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
                        {"type": "urn:orderflow:problem:invalid-request",
                         "errors": [{"field": "channel"}, {"field": "currency"},
                                    {"field": "items[0].quantity"}, {"field": "items[0].sku"},
                                    {"field": "items[0].unitPrice"}]}
                        """);
    }

    @Test
    void unknownCurrencyIsAnInvalidOrder() {
        var response = place("""
                {"channel": "WEB_STORE", "currency": "ZZZ",
                 "items": [{"sku": "ORD-ANY", "quantity": 1, "unitPrice": 1.00}]}
                """);

        assertThat(response)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:orderflow:problem:invalid-order");
    }

    @Test
    void unknownOrderIsNotFound() {
        assertThat(mvc.get().uri("/api/v1/orders/0195f2a4-7d1e-7c3a-9a55-3f1d2c4b5a69"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:orderflow:problem:order-not-found");
    }

    private MvcTestResult place(String body) {
        return mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}

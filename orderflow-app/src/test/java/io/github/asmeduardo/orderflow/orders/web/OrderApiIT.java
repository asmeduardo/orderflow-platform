package io.github.asmeduardo.orderflow.orders.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
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

    @Autowired
    ProductService products;

    @Test
    void placedOrderIsRecordedAsPendingWithExactTotals() {
        products.register("ORD-MUG", "Mug", 5);

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
    void orderWithOneShortLineIsRejectedAndReservesNothing() throws Exception {
        products.register("ORD-LAMP", "Lamp", 1);
        products.register("ORD-BULB", "Bulb", 10);

        var placed = place("""
                {"channel": "MARKETPLACE", "currency": "BRL",
                 "items": [{"sku": "ORD-BULB", "quantity": 4, "unitPrice": 9.90},
                           {"sku": "ORD-LAMP", "quantity": 2, "unitPrice": 120.00}]}
                """);

        var location = placed.getResponse().getHeader("Location");
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> "REJECTED"
                        .equals(JsonPath.read(
                                mvc.get().uri(location).exchange().getResponse().getContentAsString(), "$.status")));
        assertThat(mvc.get().uri(location))
                .bodyJson()
                .extractingPath("$.rejectionReason")
                .asString()
                .contains("ORD-LAMP");
        assertThat(products.get("ORD-BULB").reservedQuantity()).isZero();
        assertThat(products.get("ORD-LAMP").reservedQuantity()).isZero();
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

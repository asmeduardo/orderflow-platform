package io.github.asmeduardo.orderflow.orders.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class OrderListingIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ProductService products;

    @Test
    void listsNewestOrdersFirstFilteredByChannelAndStatus() {
        products.register("LST-ITEM", "Item", 2);
        place("LISTING_TEST", "LST-ITEM", 1);
        place("LISTING_TEST", "LST-ITEM", 5);
        place("LISTING_TEST", "LST-ITEM", 1);
        place("OTHER_CHANNEL", "LST-ITEM", 9);

        assertThat(mvc.get().uri("/api/v1/orders?channel=LISTING_TEST&size=2"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"content": [{"channel": "LISTING_TEST", "status": "CONFIRMED"},
                                     {"channel": "LISTING_TEST", "status": "REJECTED"}],
                         "page": 0, "size": 2, "totalElements": 3, "totalPages": 2}
                        """);
        assertThat(mvc.get().uri("/api/v1/orders?channel=LISTING_TEST&status=REJECTED"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"content": [{"status": "REJECTED", "totalAmount": 50.00, "currency": "BRL"}],
                         "totalElements": 1}
                        """);
    }

    @Test
    void summariesDoNotCarryItems() {
        products.register("LST-SUMMARY", "Item", 1);
        place("SUMMARY_TEST", "LST-SUMMARY", 1);

        assertThat(mvc.get().uri("/api/v1/orders?channel=SUMMARY_TEST"))
                .hasStatusOk()
                .bodyJson()
                .doesNotHavePath("$.content[0].items");
    }

    @Test
    void rejectsUnknownStatusAndOversizedPages() {
        assertThat(mvc.get().uri("/api/v1/orders?status=SHIPPED")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/v1/orders?size=500"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("size");
    }

    private void place(String channel, String sku, int quantity) {
        var body = """
                {"channel": "%s", "currency": "BRL",
                 "items": [{"sku": "%s", "quantity": %d, "unitPrice": 10.00}]}
                """
                .formatted(channel, sku, quantity);
        assertThat(mvc.post()
                        .uri("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .hasStatus(HttpStatus.CREATED);
    }
}

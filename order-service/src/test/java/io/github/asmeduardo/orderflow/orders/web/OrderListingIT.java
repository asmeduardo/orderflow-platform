package io.github.asmeduardo.orderflow.orders.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.StockRejectedV1;
import io.github.asmeduardo.orderflow.events.StockReservedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import io.github.asmeduardo.orderflow.support.TestEvents;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class OrderListingIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JsonMapper json;

    @Test
    void listsNewestOrdersFirstFilteredByChannelAndStatus() throws Exception {
        var first = place("LISTING_TEST", 1);
        var second = place("LISTING_TEST", 5);
        var third = place("LISTING_TEST", 1);
        place("OTHER_CHANNEL", 9);
        reserve(first);
        reject(second);
        reserve(third);
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(mvc.get().uri("/api/v1/orders?channel=LISTING_TEST&status=PENDING"))
                        .bodyJson()
                        .extractingPath("$.totalElements")
                        .isEqualTo(0));

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
    void summariesDoNotCarryItems() throws Exception {
        place("SUMMARY_TEST", 1);

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

    private void reserve(UUID orderId) {
        TestEvents.publish(
                kafka, json, Topics.INVENTORY_EVENTS, EventTypes.STOCK_RESERVED, orderId, new StockReservedV1(orderId));
    }

    private void reject(UUID orderId) {
        TestEvents.publish(
                kafka,
                json,
                Topics.INVENTORY_EVENTS,
                EventTypes.STOCK_REJECTED,
                orderId,
                new StockRejectedV1(
                        orderId,
                        List.of(new StockRejectedV1.Shortage(
                                "LST-ITEM", StockRejectedV1.Reason.INSUFFICIENT_STOCK, 5, 2))));
    }

    private UUID place(String channel, int quantity) throws UnsupportedEncodingException {
        var body = """
                {"channel": "%s", "currency": "BRL",
                 "items": [{"sku": "LST-ITEM", "quantity": %d, "unitPrice": 10.00}]}
                """.formatted(channel, quantity);
        var result = mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }
}

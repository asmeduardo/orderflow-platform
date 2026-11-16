package io.github.asmeduardo.orderflow.orders;

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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * An order waits for the inventory service. Its answers are published here exactly as the event
 * contracts define them, so the order service is tested without running the inventory service.
 */
@IntegrationTest
class OrderLifecycleIT {

    private static final Duration EVENTUALLY = Duration.ofSeconds(30);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JsonMapper json;

    @Test
    void orderIsPendingUntilTheInventoryReservesItsStock() throws Exception {
        var placed = place("LIFE-A", 2);
        assertThat(placed)
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PENDING");
        var orderId = UUID.fromString(idOf(placed));

        TestEvents.publish(
                kafka, json, Topics.INVENTORY_EVENTS, EventTypes.STOCK_RESERVED, orderId, new StockReservedV1(orderId));

        awaitStatus(orderId, "CONFIRMED");
    }

    @Test
    void orderIsRejectedWithTheShortagesReportedByTheInventory() throws Exception {
        var orderId = UUID.fromString(idOf(place("LIFE-B", 3)));

        TestEvents.publish(
                kafka,
                json,
                Topics.INVENTORY_EVENTS,
                EventTypes.STOCK_REJECTED,
                orderId,
                new StockRejectedV1(
                        orderId,
                        List.of(new StockRejectedV1.Shortage(
                                "LIFE-B", StockRejectedV1.Reason.INSUFFICIENT_STOCK, 3, 1))));

        awaitStatus(orderId, "REJECTED");
        assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId))
                .bodyJson()
                .extractingPath("$.rejectionReason")
                .asString()
                .contains("LIFE-B", "requested 3", "available 1");
    }

    @Test
    void aDuplicatedOutcomeIsAppliedOnce() throws Exception {
        var orderId = UUID.fromString(idOf(place("LIFE-C", 1)));
        var eventId = UUID.randomUUID();
        var reserved = new StockReservedV1(orderId);

        TestEvents.publish(kafka, json, Topics.INVENTORY_EVENTS, eventId, EventTypes.STOCK_RESERVED, orderId, reserved);
        TestEvents.publish(kafka, json, Topics.INVENTORY_EVENTS, eventId, EventTypes.STOCK_RESERVED, orderId, reserved);

        awaitStatus(orderId, "CONFIRMED");
        assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CONFIRMED");
    }

    private void awaitStatus(UUID orderId, String status) {
        Awaitility.await().atMost(EVENTUALLY).until(() -> status.equals(statusOf(orderId)));
    }

    private String statusOf(UUID orderId) throws UnsupportedEncodingException {
        var response = mvc.get().uri("/api/v1/orders/{id}", orderId).exchange();
        return JsonPath.read(response.getResponse().getContentAsString(), "$.status");
    }

    private MvcTestResult place(String sku, int quantity) {
        return mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"channel": "WEB_STORE", "currency": "BRL",
                         "items": [{"sku": "%s", "quantity": %d, "unitPrice": 10.00}]}
                        """.formatted(sku, quantity))
                .exchange();
    }

    private static String idOf(MvcTestResult result) throws UnsupportedEncodingException {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}

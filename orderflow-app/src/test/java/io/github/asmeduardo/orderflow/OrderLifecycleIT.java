package io.github.asmeduardo.orderflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.events.EventEnvelope;
import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.OrderPlacedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** The order and inventory modules cooperate only through events on Kafka. */
@IntegrationTest
class OrderLifecycleIT {

    private static final Duration EVENTUALLY = Duration.ofSeconds(30);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ProductService products;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JsonMapper json;

    @Test
    void orderIsPendingUntilInventoryReservesItsStock() throws Exception {
        products.register("LIFE-A", "Item", 5);

        var placed = place("LIFE-A", 2);

        assertThat(placed)
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PENDING");
        var orderId = idOf(placed);
        awaitStatus(orderId, "CONFIRMED");
        assertThat(products.get("LIFE-A").reservedQuantity()).isEqualTo(2);
    }

    @Test
    void orderIsRejectedWithTheShortagesReportedByInventory() throws Exception {
        products.register("LIFE-B", "Item", 1);

        var orderId = idOf(place("LIFE-B", 3));

        awaitStatus(orderId, "REJECTED");
        assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId))
                .bodyJson()
                .extractingPath("$.rejectionReason")
                .asString()
                .contains("LIFE-B", "requested 3", "available 1");
        assertThat(products.get("LIFE-B").reservedQuantity()).isZero();
    }

    @Test
    void cancellingAnOrderReleasesItsStockThroughEvents() throws Exception {
        products.register("LIFE-C", "Item", 4);
        var orderId = idOf(place("LIFE-C", 3));
        awaitStatus(orderId, "CONFIRMED");

        assertThat(mvc.post().uri("/api/v1/orders/{id}/cancel", orderId))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CANCELLED");

        Awaitility.await().atMost(EVENTUALLY).until(() -> products.get("LIFE-C").reservedQuantity() == 0);
    }

    @Test
    void aDuplicateDeliveryOfOrderPlacedReservesOnlyOnce() throws Exception {
        products.register("LIFE-DUP", "Item", 10);
        var orderId = UUID.randomUUID();
        var event = new EventEnvelope<>(
                UUID.randomUUID(),
                EventTypes.ORDER_PLACED.name(),
                EventTypes.ORDER_PLACED.version(),
                Instant.now(),
                orderId.toString(),
                new OrderPlacedV1(orderId, List.of(new OrderPlacedV1.Line("LIFE-DUP", 4))));
        var value = json.writeValueAsString(event);

        kafka.send(Topics.ORDER_EVENTS, orderId.toString(), value).get();
        kafka.send(Topics.ORDER_EVENTS, orderId.toString(), value).get();

        Awaitility.await()
                .atMost(EVENTUALLY)
                .until(() -> products.get("LIFE-DUP").reservedQuantity() == 4);
        // Give the second copy time to be consumed before checking that it changed nothing.
        Awaitility.await()
                .during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(10))
                .until(() -> products.get("LIFE-DUP").reservedQuantity() == 4);
        assertThat(jdbc.queryForObject(
                        "select count(*) from inventory.outbox_event where message_key = ?",
                        Integer.class,
                        orderId.toString()))
                .isEqualTo(1);
    }

    private void awaitStatus(String orderId, String status) {
        Awaitility.await().atMost(EVENTUALLY).until(() -> status.equals(statusOf(orderId)));
    }

    private String statusOf(String orderId) throws UnsupportedEncodingException {
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

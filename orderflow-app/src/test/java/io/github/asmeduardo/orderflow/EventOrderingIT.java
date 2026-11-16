package io.github.asmeduardo.orderflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.events.EventEnvelope;
import io.github.asmeduardo.orderflow.events.EventType;
import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.OrderCancelledV1;
import io.github.asmeduardo.orderflow.events.OrderPlacedV1;
import io.github.asmeduardo.orderflow.events.StockReservedV1;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/** Events of one order can arrive out of order, for example when one is replayed from the DLT. */
@IntegrationTest
class EventOrderingIT {

    private static final Duration EVENTUALLY = Duration.ofSeconds(30);

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProductService products;

    @Autowired
    MockMvcTester mvc;

    @Test
    void orderPlacedArrivingAfterItsCancellationReservesNothing() throws Exception {
        products.register("ORDERING-A", "Item", 5);
        var orderId = UUID.randomUUID();
        var cancelled = UUID.randomUUID();
        var placed = UUID.randomUUID();

        send(cancelled, EventTypes.ORDER_CANCELLED, orderId, new OrderCancelledV1(orderId));
        send(
                placed,
                EventTypes.ORDER_PLACED,
                orderId,
                new OrderPlacedV1(orderId, List.of(new OrderPlacedV1.Line("ORDERING-A", 3))));

        Awaitility.await().atMost(EVENTUALLY).until(() -> processedByInventory(placed));
        assertThat(products.get("ORDERING-A").reservedQuantity()).isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from inventory.outbox_event where message_key = ?",
                        Integer.class,
                        orderId.toString()))
                .isZero();
    }

    @Test
    void reservationOutcomeArrivingAfterTheCancellationLeavesTheOrderCancelled() throws Exception {
        products.register("ORDERING-B", "Item", 5);
        var created = mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"channel": "WEB_STORE", "currency": "BRL",
                         "items": [{"sku": "ORDERING-B", "quantity": 1, "unitPrice": 10.00}]}
                        """)
                .exchange();
        var orderId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        mvc.post().uri("/api/v1/orders/{id}/cancel", orderId).exchange();
        var lateOutcome = UUID.randomUUID();

        send(lateOutcome, EventTypes.STOCK_RESERVED, orderId, new StockReservedV1(orderId));

        Awaitility.await().atMost(EVENTUALLY).until(() -> processedByOrders(lateOutcome));
        assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
    }

    private void send(UUID eventId, EventType type, UUID orderId, Object payload) throws Exception {
        var topic = type.name().startsWith("Order") ? Topics.ORDER_EVENTS : Topics.INVENTORY_EVENTS;
        var envelope =
                new EventEnvelope<>(eventId, type.name(), type.version(), Instant.now(), orderId.toString(), payload);
        kafka.send(topic, orderId.toString(), json.writeValueAsString(envelope)).get();
    }

    private boolean processedByInventory(UUID eventId) {
        return jdbc.queryForObject(
                        "select count(*) from inventory.processed_event where event_id = ?", Integer.class, eventId)
                == 1;
    }

    private boolean processedByOrders(UUID eventId) {
        return jdbc.queryForObject(
                        "select count(*) from orders.processed_event where event_id = ?", Integer.class, eventId)
                == 1;
    }

    private String statusOf(UUID orderId) throws UnsupportedEncodingException {
        return JsonPath.read(
                mvc.get()
                        .uri("/api/v1/orders/{id}", orderId)
                        .exchange()
                        .getResponse()
                        .getContentAsString(),
                "$.status");
    }
}

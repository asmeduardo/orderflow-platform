package io.github.asmeduardo.orderflow.inventory.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.OrderCancelledV1;
import io.github.asmeduardo.orderflow.events.OrderPlacedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import io.github.asmeduardo.orderflow.support.TestEvents;
import io.github.asmeduardo.orderflow.support.TopicReader;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * The inventory service reacts to order events published exactly as the event contracts define
 * them, so it is tested without running the order service.
 */
@IntegrationTest
class OrderEventsIT {

    private static final Duration EVENTUALLY = Duration.ofSeconds(30);

    @Autowired
    ProductService products;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaContainer broker;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void orderPlacedReservesStockAndPublishesStockReserved() {
        products.register("EVT-A", "Item", 5);
        var orderId = UUID.randomUUID();

        placed(orderId, new OrderPlacedV1.Line("EVT-A", 2));

        Awaitility.await().atMost(EVENTUALLY).until(() -> products.get("EVT-A").reservedQuantity() == 2);
        var outcome = TopicReader.awaitRecords(
                        broker.getBootstrapServers(), Topics.INVENTORY_EVENTS, orderId.toString(), 1)
                .getFirst();
        assertThat(TopicReader.header(outcome, "event-type")).isEqualTo("StockReserved");
        assertThat((String) JsonPath.read(outcome.value(), "$.payload.orderId")).isEqualTo(orderId.toString());
    }

    @Test
    void anOrderWithOneShortLineReservesNothingAndPublishesTheShortages() {
        products.register("EVT-BULB", "Bulb", 10);
        products.register("EVT-LAMP", "Lamp", 1);
        var orderId = UUID.randomUUID();

        placed(orderId, new OrderPlacedV1.Line("EVT-BULB", 4), new OrderPlacedV1.Line("EVT-LAMP", 2));

        var outcome = TopicReader.awaitRecords(
                        broker.getBootstrapServers(), Topics.INVENTORY_EVENTS, orderId.toString(), 1)
                .getFirst();
        assertThat(TopicReader.header(outcome, "event-type")).isEqualTo("StockRejected");
        assertThat((String) JsonPath.read(outcome.value(), "$.payload.shortages[0].sku"))
                .isEqualTo("EVT-LAMP");
        assertThat((Integer) JsonPath.read(outcome.value(), "$.payload.shortages[0].available"))
                .isEqualTo(1);
        assertThat(products.get("EVT-BULB").reservedQuantity()).isZero();
        assertThat(products.get("EVT-LAMP").reservedQuantity()).isZero();
    }

    @Test
    void orderCancelledReleasesTheReservedStock() {
        products.register("EVT-C", "Item", 4);
        var orderId = UUID.randomUUID();
        placed(orderId, new OrderPlacedV1.Line("EVT-C", 3));
        Awaitility.await().atMost(EVENTUALLY).until(() -> products.get("EVT-C").reservedQuantity() == 3);

        TestEvents.publish(
                kafka, json, Topics.ORDER_EVENTS, EventTypes.ORDER_CANCELLED, orderId, new OrderCancelledV1(orderId));

        Awaitility.await().atMost(EVENTUALLY).until(() -> products.get("EVT-C").availableQuantity() == 4);
    }

    @Test
    void aDuplicateDeliveryOfOrderPlacedReservesOnlyOnce() {
        products.register("EVT-DUP", "Item", 10);
        var orderId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var event = new OrderPlacedV1(orderId, List.of(new OrderPlacedV1.Line("EVT-DUP", 4)));

        TestEvents.publish(kafka, json, Topics.ORDER_EVENTS, eventId, EventTypes.ORDER_PLACED, orderId, event);
        TestEvents.publish(kafka, json, Topics.ORDER_EVENTS, eventId, EventTypes.ORDER_PLACED, orderId, event);

        Awaitility.await()
                .atMost(EVENTUALLY)
                .until(() -> products.get("EVT-DUP").reservedQuantity() == 4);
        // Leave time for the second copy to be consumed before checking that it changed nothing.
        Awaitility.await()
                .during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(10))
                .until(() -> products.get("EVT-DUP").reservedQuantity() == 4);
        assertThat(outcomesFor(orderId)).isEqualTo(1);
    }

    @Test
    void orderPlacedArrivingAfterItsCancellationReservesNothing() {
        products.register("EVT-LATE", "Item", 5);
        var orderId = UUID.randomUUID();

        TestEvents.publish(
                kafka, json, Topics.ORDER_EVENTS, EventTypes.ORDER_CANCELLED, orderId, new OrderCancelledV1(orderId));
        var late = placed(orderId, new OrderPlacedV1.Line("EVT-LATE", 3));

        Awaitility.await()
                .atMost(EVENTUALLY)
                .until(() -> jdbc.queryForObject(
                                "select count(*) from inventory.processed_event where event_id = ?",
                                Integer.class,
                                late)
                        == 1);
        assertThat(products.get("EVT-LATE").reservedQuantity()).isZero();
        assertThat(outcomesFor(orderId)).isZero();
    }

    private UUID placed(UUID orderId, OrderPlacedV1.Line... lines) {
        return TestEvents.publish(
                kafka,
                json,
                Topics.ORDER_EVENTS,
                EventTypes.ORDER_PLACED,
                orderId,
                new OrderPlacedV1(orderId, List.of(lines)));
    }

    private Integer outcomesFor(UUID orderId) {
        return jdbc.queryForObject(
                "select count(*) from inventory.outbox_event where message_key = ?", Integer.class, orderId.toString());
    }
}

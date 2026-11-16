package io.github.asmeduardo.orderflow.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import io.github.asmeduardo.orderflow.support.KafkaTestConfiguration;
import io.github.asmeduardo.orderflow.support.TopicReader;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.kafka.KafkaContainer;

@IntegrationTest
class OutboxRelayIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ProductService products;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    KafkaContainer kafka;

    @Test
    void publishesRecordedEventsToKafkaKeyedByOrder() throws Exception {
        products.register("RLY-A", "Item", 5);

        var orderId = idOf(place("RLY-A"));

        var records = TopicReader.awaitRecords(kafka.getBootstrapServers(), "orderflow.order-events", orderId, 1);
        var record = records.getFirst();
        assertThat(TopicReader.header(record, "event-type")).isEqualTo("OrderPlaced");
        assertThat(TopicReader.header(record, "event-version")).isEqualTo("1");
        assertThat((String) JsonPath.read(record.value(), "$.payload.orderId")).isEqualTo(orderId);
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> outboxRow(orderId).get("published_at") != null);
    }

    @Test
    void keepsEventsWhileTheBrokerIsDownAndPublishesThemWhenItIsBack() throws Exception {
        products.register("RLY-DOWN", "Item", 5);
        // Events are relayed in order and a failed one is never overtaken, so start from an empty
        // backlog: the order placed below must be the first event the relay tries.
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from orders.outbox_event where published_at is null", Integer.class)
                        == 0);
        KafkaTestConfiguration.pause(kafka);
        String orderId;
        try {
            var placed = place("RLY-DOWN");
            assertThat(placed).hasStatus(HttpStatus.CREATED);
            orderId = idOf(placed);

            var id = orderId;
            Awaitility.await()
                    .atMost(Duration.ofSeconds(60))
                    .until(() -> (int) outboxRow(id).get("attempts") >= 1);
            var pending = outboxRow(orderId);
            assertThat(pending.get("published_at")).isNull();
            assertThat(pending.get("last_error")).isNotNull();
        } finally {
            KafkaTestConfiguration.resume(kafka);
        }

        var id = orderId;
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .until(() -> outboxRow(id).get("published_at") != null);
        // Delivery is at least once: a send that timed out may still reach the broker and be sent
        // again. Every copy carries the outbox row id as event id, which consumers deduplicate on.
        var eventId = jdbc.queryForObject(
                "select id::text from orders.outbox_event where message_key = ? and event_type = 'OrderPlaced'",
                String.class,
                orderId);
        assertThat(TopicReader.awaitRecords(kafka.getBootstrapServers(), "orderflow.order-events", orderId, 1))
                .isNotEmpty()
                .allSatisfy(record ->
                        assertThat(TopicReader.header(record, "event-id")).isEqualTo(eventId));
    }

    private Map<String, Object> outboxRow(String orderId) {
        return jdbc.queryForMap(
                "select published_at, attempts, last_error from orders.outbox_event where message_key = ? and event_type = 'OrderPlaced'",
                orderId);
    }

    private MvcTestResult place(String sku) {
        return mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"channel": "WEB_STORE", "currency": "BRL",
                         "items": [{"sku": "%s", "quantity": 1, "unitPrice": 10.00}]}
                        """.formatted(sku))
                .exchange();
    }

    private static String idOf(MvcTestResult result) throws UnsupportedEncodingException {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}

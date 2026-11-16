package io.github.asmeduardo.orderflow.orders.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.StockReservedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import io.github.asmeduardo.orderflow.support.TestEvents;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/** Events can arrive out of order, for example when one is replayed from the dead-letter topic. */
@IntegrationTest
class LateEventsIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void reservationOutcomeArrivingAfterTheCancellationLeavesTheOrderCancelled() throws Exception {
        var created = mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"channel": "WEB_STORE", "currency": "BRL",
                         "items": [{"sku": "LATE-A", "quantity": 1, "unitPrice": 10.00}]}
                        """)
                .exchange();
        var orderId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        mvc.post().uri("/api/v1/orders/{id}/cancel", orderId).exchange();

        var lateOutcome = TestEvents.publish(
                kafka, json, Topics.INVENTORY_EVENTS, EventTypes.STOCK_RESERVED, orderId, new StockReservedV1(orderId));

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from orders.processed_event where event_id = ?",
                                Integer.class,
                                lateOutcome)
                        == 1);
        assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
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

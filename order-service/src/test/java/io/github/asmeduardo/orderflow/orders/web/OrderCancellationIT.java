package io.github.asmeduardo.orderflow.orders.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.StockRejectedV1;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class OrderCancellationIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JsonMapper json;

    @Test
    void pendingOrderCanBeCancelledBeforeTheInventoryAnswers() throws Exception {
        var orderId = place("CNL-A");

        var cancelled = mvc.post().uri("/api/v1/orders/{id}/cancel", orderId).exchange();

        assertThat(cancelled)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CANCELLED");
        assertThat(orderCancelledEvents(orderId)).isEqualTo(1);
    }

    @Test
    void cancellingTwiceAnswersTheSameAndPublishesOneEvent() throws Exception {
        var orderId = place("CNL-B");
        mvc.post().uri("/api/v1/orders/{id}/cancel", orderId).exchange();

        assertThat(mvc.post().uri("/api/v1/orders/{id}/cancel", orderId))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CANCELLED");
        assertThat(orderCancelledEvents(orderId)).isEqualTo(1);
    }

    @Test
    void rejectedOrderCannotBeCancelled() throws Exception {
        var orderId = place("CNL-C");
        TestEvents.publish(
                kafka,
                json,
                Topics.INVENTORY_EVENTS,
                EventTypes.STOCK_REJECTED,
                UUID.fromString(orderId),
                new StockRejectedV1(
                        UUID.fromString(orderId),
                        List.of(new StockRejectedV1.Shortage("CNL-C", StockRejectedV1.Reason.UNKNOWN_PRODUCT, 1, 0))));
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> "REJECTED".equals(statusOf(orderId)));

        assertThat(mvc.post().uri("/api/v1/orders/{id}/cancel", orderId))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:orderflow:problem:invalid-order-state");
    }

    @Test
    void cancellingAnUnknownOrderIsNotFound() {
        assertThat(mvc.post().uri("/api/v1/orders/{id}/cancel", UUID.randomUUID()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private Integer orderCancelledEvents(String orderId) {
        return jdbc.queryForObject(
                "select count(*) from orders.outbox_event where message_key = ? and event_type = 'OrderCancelled'",
                Integer.class,
                orderId);
    }

    private String statusOf(String orderId) throws UnsupportedEncodingException {
        return JsonPath.read(
                mvc.get()
                        .uri("/api/v1/orders/{id}", orderId)
                        .exchange()
                        .getResponse()
                        .getContentAsString(),
                "$.status");
    }

    private String place(String sku) throws UnsupportedEncodingException {
        var result = mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"channel": "WEB_STORE", "currency": "BRL",
                         "items": [{"sku": "%s", "quantity": 1, "unitPrice": 10.00}]}
                        """.formatted(sku))
                .exchange();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}

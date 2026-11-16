package io.github.asmeduardo.orderflow.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class OrderOutboxIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void placingAnOrderRecordsAnOrderPlacedEventInTheSameTransaction() throws Exception {

        var orderId = idOf(place(UUID.randomUUID().toString(), "OBX-A", 2));

        var events = eventsOf(orderId);
        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event)
                .containsEntry("topic", "orderflow.order-events")
                .containsEntry("event_type", "OrderPlaced")
                .containsEntry("event_version", 1)
                .containsEntry("published_at", null);
        String envelope = (String) event.get("envelope");
        assertThat((String) JsonPath.read(envelope, "$.eventId"))
                .isEqualTo(event.get("id").toString());
        assertThat((String) JsonPath.read(envelope, "$.payload.orderId")).isEqualTo(orderId);
        assertThat(JsonPath.<List<Object>>read(envelope, "$.payload.lines")).hasSize(1);
        assertThat((Integer) JsonPath.read(envelope, "$.payload.lines[0].quantity"))
                .isEqualTo(2);
    }

    @Test
    void cancellingAnOrderRecordsAnOrderCancelledEventOnce() throws Exception {
        var orderId = idOf(place(UUID.randomUUID().toString(), "OBX-B", 1));

        mvc.post().uri("/api/v1/orders/{id}/cancel", orderId).exchange();
        mvc.post().uri("/api/v1/orders/{id}/cancel", orderId).exchange();

        assertThat(eventsOf(orderId))
                .extracting(event -> event.get("event_type"))
                .containsExactly("OrderPlaced", "OrderCancelled");
    }

    @Test
    void transactionsThatRollBackLeaveNoEventBehind() throws Exception {
        var key = UUID.randomUUID().toString();
        int clients = 8;
        var start = new CountDownLatch(1);
        var ids = new ArrayList<String>();
        try (var executor = Executors.newFixedThreadPool(clients)) {
            var futures = new ArrayList<Future<MvcTestResult>>();
            for (int i = 0; i < clients; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return place(key, "OBX-RACE", 1);
                }));
            }
            start.countDown();
            for (var future : futures) {
                ids.add(idOf(future.get(60, TimeUnit.SECONDS)));
            }
        }

        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(jdbc.queryForObject(
                        "select count(*) from orders.outbox_event where payload -> 'payload' -> 'lines' -> 0 ->> 'sku' = ?",
                        Integer.class,
                        "OBX-RACE"))
                .isEqualTo(1);
    }

    private List<Map<String, Object>> eventsOf(String orderId) {
        return jdbc.queryForList("""
                select id, topic, message_key, event_type, event_version, payload::text as envelope, published_at
                  from orders.outbox_event
                 where message_key = ?
                 order by created_at
                """, orderId);
    }

    private MvcTestResult place(String key, String sku, int quantity) {
        return mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", key)
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

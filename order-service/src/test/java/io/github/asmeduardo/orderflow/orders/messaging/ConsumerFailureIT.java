package io.github.asmeduardo.orderflow.orders.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.events.EventEnvelope;
import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.StockReservedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.support.FlakyListenerConfiguration;
import io.github.asmeduardo.orderflow.support.FlakyListenerConfiguration.FlakyListener;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import io.github.asmeduardo.orderflow.support.TopicReader;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class ConsumerFailureIT {

    private static final String INVENTORY_EVENTS_DLT = Topics.INVENTORY_EVENTS + Topics.DEAD_LETTER_SUFFIX;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaContainer broker;

    @Autowired
    JsonMapper json;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    FlakyListener flaky;

    @Test
    void malformedEventGoesToTheDeadLetterTopicAndDoesNotBlockTheNextOnes() throws Exception {
        var poisonKey = UUID.randomUUID().toString();
        var nextOrder = placeOrder();

        kafka.send(Topics.INVENTORY_EVENTS, poisonKey, "{this is not json").get();
        kafka.send(Topics.INVENTORY_EVENTS, nextOrder.toString(), stockReserved(nextOrder, 1))
                .get();

        var dead = TopicReader.awaitRecords(broker.getBootstrapServers(), INVENTORY_EVENTS_DLT, poisonKey, 1);
        assertThat(dead.getFirst().value()).isEqualTo("{this is not json");
        assertThat(TopicReader.header(dead.getFirst(), "kafka_dlt-original-topic"))
                .isEqualTo(Topics.INVENTORY_EVENTS);
        assertThat(TopicReader.header(dead.getFirst(), "kafka_dlt-exception-cause-fqcn"))
                .startsWith("tools.jackson.");
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> "CONFIRMED".equals(statusOf(nextOrder)));
    }

    @Test
    void eventInAnUnsupportedVersionGoesToTheDeadLetterTopic() throws Exception {
        var orderId = UUID.randomUUID();

        kafka.send(Topics.INVENTORY_EVENTS, orderId.toString(), stockReserved(orderId, 99))
                .get();

        var dead = TopicReader.awaitRecords(broker.getBootstrapServers(), INVENTORY_EVENTS_DLT, orderId.toString(), 1);
        assertThat(TopicReader.header(dead.getFirst(), "kafka_dlt-exception-cause-fqcn"))
                .endsWith("UnsupportedEventVersionException");
    }

    @Test
    void transientFailuresAreRetriedWithGrowingDelays() throws Exception {
        var key = UUID.randomUUID().toString();
        flaky.failNext(key, 2);

        kafka.send(FlakyListenerConfiguration.TOPIC, key, "work").get();

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> flaky.attemptsOf(key).size() == 3);
        var attempts = flaky.attemptsOf(key);
        var firstDelay = Duration.between(attempts.get(0), attempts.get(1));
        var secondDelay = Duration.between(attempts.get(1), attempts.get(2));
        assertThat(firstDelay).isGreaterThanOrEqualTo(Duration.ofMillis(400));
        assertThat(secondDelay).isGreaterThan(firstDelay);
    }

    @Test
    void persistentFailureEndsInTheDeadLetterTopicAfterBoundedRetries() throws Exception {
        var key = UUID.randomUUID().toString();
        flaky.failNext(key, Integer.MAX_VALUE);

        kafka.send(FlakyListenerConfiguration.TOPIC, key, "always fails").get();

        var dead = TopicReader.awaitRecords(
                broker.getBootstrapServers(), FlakyListenerConfiguration.TOPIC + ".DLT", key, 1);
        assertThat(dead.getFirst().value()).isEqualTo("always fails");
        assertThat(flaky.attemptsOf(key)).hasSize(4);
    }

    private String stockReserved(UUID orderId, int version) {
        return json.writeValueAsString(new EventEnvelope<>(
                UUID.randomUUID(),
                EventTypes.STOCK_RESERVED.name(),
                version,
                Instant.now(),
                orderId.toString(),
                new StockReservedV1(orderId)));
    }

    private UUID placeOrder() throws UnsupportedEncodingException {
        var result = mvc.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"channel": "WEB_STORE", "currency": "BRL",
                         "items": [{"sku": "DLT-NEXT", "quantity": 1, "unitPrice": 10.00}]}
                        """)
                .exchange();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
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

package io.github.asmeduardo.orderflow.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.orderflow.events.EventEnvelope;
import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.OrderPlacedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.support.FlakyListenerConfiguration;
import io.github.asmeduardo.orderflow.support.FlakyListenerConfiguration.FlakyListener;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import io.github.asmeduardo.orderflow.support.TopicReader;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class ConsumerFailureIT {

    private static final String ORDER_EVENTS_DLT = Topics.ORDER_EVENTS + Topics.DEAD_LETTER_SUFFIX;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaContainer broker;

    @Autowired
    JsonMapper json;

    @Autowired
    ProductService products;

    @Autowired
    FlakyListener flaky;

    @Test
    void malformedEventGoesToTheDeadLetterTopicAndDoesNotBlockTheNextOnes() throws Exception {
        products.register("DLT-NEXT", "Item", 5);
        var poisonKey = UUID.randomUUID().toString();

        kafka.send(Topics.ORDER_EVENTS, poisonKey, "{this is not json").get();
        var nextOrder = UUID.randomUUID();
        kafka.send(Topics.ORDER_EVENTS, nextOrder.toString(), orderPlaced(nextOrder, 1, "DLT-NEXT", 2))
                .get();

        var dead = TopicReader.awaitRecords(broker.getBootstrapServers(), ORDER_EVENTS_DLT, poisonKey, 1);
        assertThat(dead.getFirst().value()).isEqualTo("{this is not json");
        assertThat(TopicReader.header(dead.getFirst(), "kafka_dlt-original-topic"))
                .isEqualTo(Topics.ORDER_EVENTS);
        assertThat(TopicReader.header(dead.getFirst(), "kafka_dlt-exception-cause-fqcn"))
                .startsWith("tools.jackson.");
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> products.get("DLT-NEXT").reservedQuantity() == 2);
    }

    @Test
    void eventInAnUnsupportedVersionGoesToTheDeadLetterTopic() throws Exception {
        var orderId = UUID.randomUUID();

        kafka.send(Topics.ORDER_EVENTS, orderId.toString(), orderPlaced(orderId, 99, "DLT-ANY", 1))
                .get();

        var dead = TopicReader.awaitRecords(broker.getBootstrapServers(), ORDER_EVENTS_DLT, orderId.toString(), 1);
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

    private String orderPlaced(UUID orderId, int version, String sku, int quantity) {
        return json.writeValueAsString(new EventEnvelope<>(
                UUID.randomUUID(),
                EventTypes.ORDER_PLACED.name(),
                version,
                Instant.now(),
                orderId.toString(),
                new OrderPlacedV1(orderId, List.of(new OrderPlacedV1.Line(sku, quantity)))));
    }
}

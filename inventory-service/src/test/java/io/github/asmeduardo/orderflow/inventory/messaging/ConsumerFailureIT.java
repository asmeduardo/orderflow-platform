package io.github.asmeduardo.orderflow.inventory.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.orderflow.events.EventEnvelope;
import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.OrderPlacedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.inventory.application.ProductService;
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

    @Test
    void malformedEventGoesToTheDeadLetterTopicAndDoesNotBlockTheNextOnes() throws Exception {
        products.register("DLT-NEXT", "Item", 5);
        var poisonKey = UUID.randomUUID().toString();
        var nextOrder = UUID.randomUUID();

        kafka.send(Topics.ORDER_EVENTS, poisonKey, "{this is not json").get();
        kafka.send(Topics.ORDER_EVENTS, nextOrder.toString(), orderPlaced(nextOrder, 1, "DLT-NEXT", 2))
                .get();

        var dead = TopicReader.awaitRecords(broker.getBootstrapServers(), ORDER_EVENTS_DLT, poisonKey, 1);
        assertThat(dead.getFirst().value()).isEqualTo("{this is not json");
        assertThat(TopicReader.header(dead.getFirst(), "kafka_dlt-exception-cause-fqcn"))
                .startsWith("tools.jackson.");
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> products.get("DLT-NEXT").reservedQuantity() == 2);
    }

    @Test
    void eventInAnUnsupportedVersionGoesToTheDeadLetterTopic() throws Exception {
        var orderId = UUID.randomUUID();

        kafka.send(Topics.ORDER_EVENTS, orderId.toString(), orderPlaced(orderId, 2, "DLT-ANY", 1))
                .get();

        var dead = TopicReader.awaitRecords(broker.getBootstrapServers(), ORDER_EVENTS_DLT, orderId.toString(), 1);
        assertThat(TopicReader.header(dead.getFirst(), "kafka_dlt-exception-cause-fqcn"))
                .endsWith("UnsupportedEventVersionException");
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

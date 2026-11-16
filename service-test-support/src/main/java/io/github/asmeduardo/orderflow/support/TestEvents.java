package io.github.asmeduardo.orderflow.support;

import io.github.asmeduardo.orderflow.events.EventEnvelope;
import io.github.asmeduardo.orderflow.events.EventType;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes events as the other service would, so each service can be tested against the event
 * contracts without running its counterpart.
 */
public final class TestEvents {

    private TestEvents() {}

    /** Publishes the event with a fresh id and returns that id. */
    public static UUID publish(
            KafkaTemplate<String, String> kafka,
            JsonMapper json,
            String topic,
            EventType type,
            UUID orderId,
            Object payload) {
        var eventId = UUID.randomUUID();
        publish(kafka, json, topic, eventId, type, orderId, payload);
        return eventId;
    }

    /** Publishes the event with the given id; reuse an id to simulate a duplicate delivery. */
    public static void publish(
            KafkaTemplate<String, String> kafka,
            JsonMapper json,
            String topic,
            UUID eventId,
            EventType type,
            UUID orderId,
            Object payload) {
        var envelope =
                new EventEnvelope<>(eventId, type.name(), type.version(), Instant.now(), orderId.toString(), payload);
        try {
            kafka.send(topic, orderId.toString(), json.writeValueAsString(envelope))
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }
}

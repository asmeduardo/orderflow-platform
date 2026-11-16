package io.github.asmeduardo.orderflow.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Metadata shared by every event. {@code eventType} and {@code eventVersion} tell the consumer how
 * to read {@code payload}; {@code aggregateId} is also the Kafka record key, so all events of one
 * order keep their relative order in a partition. Consumers deduplicate by {@code eventId}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope<T>(
        UUID eventId, String eventType, int eventVersion, Instant occurredAt, String aggregateId, T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateId, "aggregateId");
    }

    public boolean is(EventType type) {
        return type.name().equals(eventType) && type.version() == eventVersion;
    }
}

package io.github.asmeduardo.orderflow.shared.messaging;

import io.github.asmeduardo.orderflow.events.EventEnvelope;
import io.github.asmeduardo.orderflow.events.EventType;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads the envelope first, then the payload in the schema version the envelope announces. */
@Component
public class EventReader {

    private static final TypeReference<EventEnvelope<JsonNode>> ENVELOPE = new TypeReference<>() {};

    private final JsonMapper json;

    public EventReader(JsonMapper json) {
        this.json = json;
    }

    public EventEnvelope<JsonNode> envelope(String value) {
        return json.readValue(value, ENVELOPE);
    }

    /**
     * Fails for an event whose type the consumer handles but in a version it does not know. Types the
     * consumer does not handle at all are not an error: a topic can carry events for other readers.
     */
    public void requireSupportedVersion(EventEnvelope<?> envelope, EventType... handled) {
        for (var type : handled) {
            if (type.name().equals(envelope.eventType()) && type.version() != envelope.eventVersion()) {
                throw new UnsupportedEventVersionException(envelope.eventType(), envelope.eventVersion());
            }
        }
    }

    public <T> T payload(EventEnvelope<JsonNode> envelope, EventType type, Class<T> payloadType) {
        if (!envelope.is(type)) {
            throw new IllegalArgumentException(
                    "Expected " + type + " but got " + envelope.eventType() + " v" + envelope.eventVersion());
        }
        return json.treeToValue(envelope.payload(), payloadType);
    }
}

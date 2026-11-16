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

    public <T> T payload(EventEnvelope<JsonNode> envelope, EventType type, Class<T> payloadType) {
        if (!envelope.is(type)) {
            throw new IllegalArgumentException(
                    "Expected " + type + " but got " + envelope.eventType() + " v" + envelope.eventVersion());
        }
        return json.treeToValue(envelope.payload(), payloadType);
    }
}

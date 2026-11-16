package io.github.asmeduardo.orderflow.shared.messaging;

import io.github.asmeduardo.orderflow.events.EventEnvelope;
import io.github.asmeduardo.orderflow.events.EventType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Appends events to an outbox table owned by one module. The insert joins the transaction that
 * changes the aggregate, so the event is stored if and only if the change is committed; a relay
 * publishes it later. Each module has its own table, keeping data ownership per schema.
 */
public class TransactionalOutbox {

    private static final Pattern TABLE_NAME = Pattern.compile("[a-z_]+\\.[a-z_]+");

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;
    private final String insert;

    public TransactionalOutbox(JdbcTemplate jdbc, JsonMapper json, Clock clock, String table) {
        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Not a schema-qualified table name: " + table);
        }
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.insert = "insert into " + table
                + " (id, topic, message_key, event_type, event_version, payload, created_at)"
                + " values (?, ?, ?, ?, ?, ?::jsonb, ?)";
    }

    /** Records the event; must run inside the transaction that changes the aggregate. */
    public EventEnvelope<?> append(String topic, EventType type, String aggregateId, Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outbox events must be appended inside the business transaction");
        }
        var event = new EventEnvelope<>(
                UUID.randomUUID(), type.name(), type.version(), clock.instant(), aggregateId, payload);
        jdbc.update(
                insert,
                event.eventId(),
                topic,
                aggregateId,
                type.name(),
                type.version(),
                json.writeValueAsString(event),
                OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC));
        return event;
    }
}

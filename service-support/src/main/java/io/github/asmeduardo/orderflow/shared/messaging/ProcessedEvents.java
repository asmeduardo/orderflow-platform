package io.github.asmeduardo.orderflow.shared.messaging;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Consumer side of at-least-once delivery. The handler marks the event as processed before applying
 * its effects, in the same transaction: a duplicate delivery finds the mark and is skipped, while a
 * handling that fails rolls the mark back so the redelivered event is processed again.
 */
public class ProcessedEvents {

    private static final Pattern TABLE_NAME = Pattern.compile("[a-z_]+\\.[a-z_]+");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String insert;

    public ProcessedEvents(JdbcTemplate jdbc, Clock clock, String table) {
        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Not a schema-qualified table name: " + table);
        }
        this.jdbc = jdbc;
        this.clock = clock;
        this.insert = "insert into " + table + " (event_id, event_type, processed_at) values (?, ?, ?)"
                + " on conflict (event_id) do nothing";
    }

    /**
     * Records the event as processed.
     *
     * @return {@code true} the first time, {@code false} for a duplicate that must be skipped
     */
    public boolean markProcessed(UUID eventId, String eventType) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Events must be marked inside the transaction that applies their effects");
        }
        return jdbc.update(insert, eventId, eventType, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)) == 1;
    }
}

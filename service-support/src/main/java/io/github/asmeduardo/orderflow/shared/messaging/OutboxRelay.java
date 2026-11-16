package io.github.asmeduardo.orderflow.shared.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Publishes the pending rows of one outbox table to Kafka, oldest first. Rows are claimed with
 * {@code FOR UPDATE SKIP LOCKED}, so several instances can relay the same table without sending a
 * row twice at the same time. A row is marked as published only after the broker acknowledged it;
 * when a send fails the row stays pending with its attempt count and error, and the batch stops so
 * that later events of the same order never overtake it. Delivery is therefore at least once:
 * consumers deduplicate by event id.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Pattern TABLE_NAME = Pattern.compile("[a-z_]+\\.[a-z_]+");
    private static final int MAX_ERROR_LENGTH = 1000;

    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final String table;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxRelay(
            JdbcTemplate jdbc,
            TransactionOperations transactions,
            KafkaTemplate<String, String> kafka,
            Clock clock,
            String table,
            int batchSize,
            Duration sendTimeout) {
        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Not a schema-qualified table name: " + table);
        }
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.kafka = kafka;
        this.clock = clock;
        this.table = table;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    /** Publishes up to one batch of pending events and returns how many were published. */
    public int publishPending() {
        Integer published = transactions.execute(status -> {
            var pending = jdbc.query(
                    "select id, topic, message_key, event_type, event_version, payload::text as payload from " + table
                            + " where published_at is null order by position limit ? for update skip locked",
                    (rs, row) -> new PendingEvent(
                            rs.getObject("id", UUID.class),
                            rs.getString("topic"),
                            rs.getString("message_key"),
                            rs.getString("event_type"),
                            rs.getInt("event_version"),
                            rs.getString("payload")),
                    batchSize);
            int count = 0;
            for (var event : pending) {
                try {
                    send(event);
                } catch (Exception e) {
                    recordFailure(event, e);
                    break;
                }
                jdbc.update(
                        "update " + table
                                + " set published_at = ?, attempts = attempts + 1, last_error = null where id = ?",
                        OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                        event.id());
                count++;
            }
            return count;
        });
        return published == null ? 0 : published;
    }

    private void send(PendingEvent event) throws Exception {
        var record = new ProducerRecord<>(event.topic(), event.key(), event.payload());
        record.headers().add("event-id", bytes(event.id().toString()));
        record.headers().add("event-type", bytes(event.type()));
        record.headers().add("event-version", bytes(Integer.toString(event.version())));
        try {
            kafka.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private void recordFailure(PendingEvent event, Exception error) {
        log.warn(
                "Could not publish outbox event {} from {}; it stays pending: {}", event.id(), table, error.toString());
        var message = error.toString();
        jdbc.update(
                "update " + table + " set attempts = attempts + 1, last_error = ? where id = ?",
                message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH),
                event.id());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record PendingEvent(UUID id, String topic, String key, String type, int version, String payload) {}
}

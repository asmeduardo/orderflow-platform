package io.github.asmeduardo.orderflow.orders.application;

import io.github.asmeduardo.orderflow.shared.messaging.OutboxProperties;
import io.github.asmeduardo.orderflow.shared.messaging.OutboxRelay;
import io.github.asmeduardo.orderflow.shared.messaging.ProcessedEvents;
import io.github.asmeduardo.orderflow.shared.messaging.TransactionalOutbox;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
/** The orders module publishes and deduplicates events through tables of its own schema. */
class OrdersMessagingConfiguration {

    private static final String OUTBOX_TABLE = "orders.outbox_event";

    @Bean
    TransactionalOutbox ordersOutbox(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        return new TransactionalOutbox(jdbc, json, clock, OUTBOX_TABLE);
    }

    @Bean
    OutboxRelay ordersOutboxRelay(
            JdbcTemplate jdbc,
            TransactionOperations transactions,
            KafkaTemplate<String, String> kafka,
            Clock clock,
            OutboxProperties properties) {
        return new OutboxRelay(
                jdbc, transactions, kafka, clock, OUTBOX_TABLE, properties.batchSize(), properties.sendTimeout());
    }

    @Bean
    ProcessedEvents ordersProcessedEvents(JdbcTemplate jdbc, Clock clock) {
        return new ProcessedEvents(jdbc, clock, "orders.processed_event");
    }
}

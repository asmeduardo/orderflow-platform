package io.github.asmeduardo.orderflow.orders.application;

import io.github.asmeduardo.orderflow.shared.messaging.TransactionalOutbox;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class OrdersOutboxConfiguration {

    /** The orders module writes only to its own outbox table. */
    @Bean
    TransactionalOutbox ordersOutbox(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        return new TransactionalOutbox(jdbc, json, clock, "orders.outbox_event");
    }
}

package io.github.asmeduardo.orderflow.shared.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retry policy of every Kafka listener: {@code maxRetries} redeliveries after the first attempt,
 * waiting {@code initialInterval}, then multiplying the wait by {@code multiplier} up to {@code
 * maxInterval}. A record that still fails goes to the dead-letter topic.
 */
@ConfigurationProperties("orderflow.consumer.retry")
public record ConsumerRetryProperties(
        @DefaultValue("3") int maxRetries,
        @DefaultValue("500ms") Duration initialInterval,
        @DefaultValue("2.0") double multiplier,
        @DefaultValue("5s") Duration maxInterval) {}

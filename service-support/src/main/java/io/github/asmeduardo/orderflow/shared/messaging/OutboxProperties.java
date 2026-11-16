package io.github.asmeduardo.orderflow.shared.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param pollInterval delay between relay runs, which is also the retry delay after a failed send
 * @param batchSize maximum number of events published per run and table
 * @param sendTimeout how long the relay waits for the broker to acknowledge one event; keep it above
 *     the producer {@code delivery.timeout.ms}, so the relay never gives up on a send the producer is
 *     still retrying, which would only add duplicates
 */
@ConfigurationProperties("orderflow.outbox")
public record OutboxProperties(
        @DefaultValue("500ms") Duration pollInterval,
        @DefaultValue("100") int batchSize,
        @DefaultValue("15s") Duration sendTimeout) {}

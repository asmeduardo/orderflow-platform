package io.github.asmeduardo.orderflow.shared.messaging;

import io.github.asmeduardo.orderflow.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

/**
 * What happens when a listener throws. Retries are blocking: the partition waits for the failed
 * record, so events of the same order are never applied out of order. Retries are bounded and
 * exponentially spaced; afterwards, or immediately for errors that a retry cannot fix, the record
 * is copied to {@code <topic>.DLT} with the exception in its headers, and consumption moves on.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConsumerRetryProperties.class)
class ConsumerErrorHandlingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConsumerErrorHandlingConfiguration.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, ConsumerRetryProperties retry) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka, (record, exception) -> {
            log.error(
                    "Moving record {}-{}@{} to the dead-letter topic: {}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception.toString());
            return new TopicPartition(record.topic() + Topics.DEAD_LETTER_SUFFIX, record.partition());
        });
        var backOff = new ExponentialBackOff(retry.initialInterval().toMillis(), retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());
        backOff.setMaxAttempts(retry.maxRetries());
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(
                JacksonException.class, DeserializationException.class, UnsupportedEventVersionException.class);
        return handler;
    }

    /** Same partition count as the source topics, because a dead record keeps its partition. */
    @Bean
    NewTopic orderEventsDeadLetterTopic() {
        return TopicBuilder.name(Topics.ORDER_EVENTS + Topics.DEAD_LETTER_SUFFIX)
                .partitions(3)
                .build();
    }

    @Bean
    NewTopic inventoryEventsDeadLetterTopic() {
        return TopicBuilder.name(Topics.INVENTORY_EVENTS + Topics.DEAD_LETTER_SUFFIX)
                .partitions(3)
                .build();
    }
}

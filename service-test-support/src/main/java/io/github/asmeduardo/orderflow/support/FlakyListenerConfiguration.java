package io.github.asmeduardo.orderflow.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;

/**
 * A listener whose failures the test controls. It runs in the application context with the same
 * container factory and error handler as the production listeners, so it exercises their retry and
 * dead-letter policy without having to break the database or the broker.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FlakyListenerConfiguration {

    public static final String TOPIC = "orderflow.test-flaky";

    @Bean
    NewTopic flakyTopic() {
        return TopicBuilder.name(TOPIC).partitions(1).build();
    }

    @Bean
    NewTopic flakyDeadLetterTopic() {
        return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
    }

    @Bean
    FlakyListener flakyListener() {
        return new FlakyListener();
    }

    public static class FlakyListener {

        private final Map<String, Integer> failuresLeft = new ConcurrentHashMap<>();
        private final Map<String, List<Instant>> attempts = new ConcurrentHashMap<>();

        /** The next {@code times} deliveries of {@code key} fail; use a large number to always fail. */
        public void failNext(String key, int times) {
            failuresLeft.put(key, times);
        }

        public List<Instant> attemptsOf(String key) {
            return attempts.getOrDefault(key, List.of());
        }

        @KafkaListener(id = "test-flaky", topics = TOPIC, groupId = "test-flaky")
        void on(ConsumerRecord<String, String> record) {
            attempts.computeIfAbsent(record.key(), key -> new CopyOnWriteArrayList<>())
                    .add(Instant.now());
            if (failuresLeft.merge(record.key(), -1, Integer::sum) >= 0) {
                throw new IllegalStateException("Simulated transient failure for " + record.key());
            }
        }
    }
}

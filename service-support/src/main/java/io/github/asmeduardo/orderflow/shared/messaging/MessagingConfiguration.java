package io.github.asmeduardo.orderflow.shared.messaging;

import io.github.asmeduardo.orderflow.events.Topics;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
class MessagingConfiguration {

    /** Topics are declared here because brokers in every environment run with auto-creation off. */
    @Bean
    NewTopic orderEventsTopic() {
        return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(3).build();
    }

    @Bean
    NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(Topics.INVENTORY_EVENTS).partitions(3).build();
    }

    @Bean
    OutboxRelayScheduler outboxRelayScheduler(List<OutboxRelay> relays) {
        return new OutboxRelayScheduler(relays);
    }

    /** Polls every outbox table; a failed send waits for the next poll, which is the retry delay. */
    static class OutboxRelayScheduler {

        private final List<OutboxRelay> relays;

        OutboxRelayScheduler(List<OutboxRelay> relays) {
            this.relays = relays;
        }

        @Scheduled(fixedDelayString = "${orderflow.outbox.poll-interval:500ms}")
        void relay() {
            relays.forEach(OutboxRelay::publishPending);
        }
    }
}

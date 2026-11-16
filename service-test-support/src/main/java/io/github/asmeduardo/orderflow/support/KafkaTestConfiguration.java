package io.github.asmeduardo.orderflow.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

/** A single-node KRaft broker, the same image used by {@code compose.yaml}. */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka-native:4.2.2");
    }

    /** Freezes the broker process, so clients see it as unreachable without losing its data. */
    public static void pause(KafkaContainer kafka) {
        DockerClientFactory.instance()
                .client()
                .pauseContainerCmd(kafka.getContainerId())
                .exec();
    }

    public static void resume(KafkaContainer kafka) {
        DockerClientFactory.instance()
                .client()
                .unpauseContainerCmd(kafka.getContainerId())
                .exec();
    }
}

package io.github.asmeduardo.orderflow.shared;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfiguration {

    /**
     * Every persisted timestamp is a UTC instant read from this clock, so tests can control time.
     * It ticks in microseconds, the precision of PostgreSQL {@code timestamptz}, so a response
     * never shows a value that differs from what was stored.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}

package io.github.asmeduardo.orderflow.shared;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfiguration {

    /** Every persisted timestamp is a UTC instant read from this clock, so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

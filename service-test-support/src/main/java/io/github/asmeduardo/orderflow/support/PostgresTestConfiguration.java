package io.github.asmeduardo.orderflow.support;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts the same PostgreSQL major version used by {@code compose.yaml}. Its data directory lives
 * in memory and fsync is off: test data is disposable, and this keeps runs fast and stops every
 * run from writing to the Docker disk.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine")
                .withTmpFs(Map.of("/var/lib/postgresql", "rw"))
                .withCommand("postgres", "-c", "fsync=off", "-c", "full_page_writes=off");
    }
}

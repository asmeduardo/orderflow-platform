package io.github.asmeduardo.orderflow.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Boots the full application against a real PostgreSQL started by Testcontainers. Every test
 * annotated with it shares one cached application context, and therefore one database container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(PostgresTestConfiguration.class)
public @interface IntegrationTest {}

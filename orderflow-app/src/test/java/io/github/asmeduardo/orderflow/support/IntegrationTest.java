package io.github.asmeduardo.orderflow.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

/**
 * Boots the full application against a real PostgreSQL started by Testcontainers. Every test
 * annotated with it shares one cached application context, and therefore one database container.
 * The auto-configured {@code MockMvc} sends an operator token on every request (see {@link
 * DefaultTokenConfiguration}); the resource server trusts the key from {@link TestJwtKeys}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, DefaultTokenConfiguration.class})
@ContextConfiguration(initializers = TestJwtKeys.class)
public @interface IntegrationTest {}

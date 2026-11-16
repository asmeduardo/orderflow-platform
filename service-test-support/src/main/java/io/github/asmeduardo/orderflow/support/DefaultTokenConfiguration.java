package io.github.asmeduardo.orderflow.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Functional integration tests act as a trusted operator client, so they can focus on behaviour.
 * Authorization itself is covered by tests that build their own {@code MockMvc} without this token.
 */
@TestConfiguration(proxyBeanMethods = false)
class DefaultTokenConfiguration {

    static final String CLIENT_ID = "integration-test-client";

    @Bean
    MockMvcBuilderCustomizer operatorTokenOnEveryRequest() {
        var operator = jwt().jwt(token -> token.subject(CLIENT_ID))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_orderflow.read"),
                        new SimpleGrantedAuthority("SCOPE_orderflow.operate"));
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").with(operator));
    }
}

package io.github.asmeduardo.orderflow.devauth;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration(proxyBeanMethods = false)
class AccessTokenConfiguration {

    /** Audience the OrderFlow API requires; tokens minted for anything else are refused there. */
    static final String ORDERFLOW_API = "orderflow-api";

    /**
     * By default the audience of an access token is the client id. The API instead checks that the
     * token was issued for it, so every access token names the API as its audience.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> orderflowAudience() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims().audience(List.of(ORDERFLOW_API));
            }
        };
    }
}

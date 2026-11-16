package io.github.asmeduardo.orderflow.shared.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The API is an OAuth 2.0 resource server: every call carries a JWT access token, and the scopes in
 * it decide what the caller may do. Reading requires {@value Scopes#READ}; any change requires
 * {@value Scopes#OPERATE}. The two scopes are independent, so a client gets exactly what it needs.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailsSecurityHandler problems) throws Exception {
        http.authorizeHttpRequests(requests -> requests.requestMatchers(
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAuthority(Scopes.authority(Scopes.READ))
                        .requestMatchers("/api/**")
                        .hasAuthority(Scopes.authority(Scopes.OPERATE))
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults())
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer tokens are never sent automatically by browsers, so there is no CSRF exposure.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}

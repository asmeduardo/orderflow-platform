package io.github.asmeduardo.orderflow.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String BEARER_JWT = "bearer-jwt";

    @Bean
    OpenAPI orderflowOpenApi(@Value("${orderflow.api.title:OrderFlow API}") String title) {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_JWT,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Access token with scope orderflow.read and/or orderflow.operate")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT))
                .info(new Info()
                        .title(title)
                        .version("v1")
                        .description("""
                                Order intake and stock reservation for multi-channel retailers. \
                                Errors follow RFC 9457 (application/problem+json); problem types are \
                                URNs of the form urn:orderflow:problem:<slug>.""")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}

package io.github.asmeduardo.orderflow.shared.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI orderflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OrderFlow API")
                        .version("v1")
                        .description("""
                                Order intake and stock reservation for multi-channel retailers. \
                                Errors follow RFC 9457 (application/problem+json); problem types are \
                                URNs of the form urn:orderflow:problem:<slug>.""")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}

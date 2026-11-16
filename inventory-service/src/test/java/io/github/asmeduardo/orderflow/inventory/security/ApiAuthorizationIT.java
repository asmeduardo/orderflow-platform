package io.github.asmeduardo.orderflow.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import io.github.asmeduardo.orderflow.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** The inventory API applies the same scopes as the order API. */
@IntegrationTest
class ApiAuthorizationIT {

    @Autowired
    WebApplicationContext context;

    MockMvcTester mvc;

    @BeforeEach
    void buildMockMvcWithoutDefaultToken() {
        mvc = MockMvcTester.create(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build());
    }

    @Test
    void productsRequireABearerToken() {
        assertThat(mvc.get().uri("/api/v1/products"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:orderflow:problem:unauthorized");
    }

    @Test
    void readScopeCanQueryButOnlyOperateScopeCanRegister() {
        var read = jwt().authorities(new SimpleGrantedAuthority("SCOPE_orderflow.read"));
        var operate = jwt().authorities(new SimpleGrantedAuthority("SCOPE_orderflow.operate"));
        var product = "{\"sku\": \"AUTH-INV\", \"name\": \"Item\", \"onHandQuantity\": 1}";

        assertThat(mvc.get().uri("/api/v1/products").with(read)).hasStatusOk();
        assertThat(mvc.post()
                        .uri("/api/v1/products")
                        .with(read)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post()
                        .uri("/api/v1/products")
                        .with(operate)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product))
                .hasStatus(HttpStatus.CREATED);
    }
}

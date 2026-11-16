package io.github.asmeduardo.orderflow.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Who may call what. Uses its own MockMvc, without the operator token of functional tests. */
@IntegrationTest
class ApiAuthorizationIT {

    private static final String READ = "SCOPE_orderflow.read";
    private static final String OPERATE = "SCOPE_orderflow.operate";

    @Autowired
    WebApplicationContext context;

    MockMvcTester mvc;

    @BeforeEach
    void buildMockMvcWithoutDefaultToken() {
        mvc = MockMvcTester.create(
                MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build());
    }

    @ParameterizedTest(name = "{0} {1} without a token is 401")
    @CsvSource({
        "GET,  /api/v1/products",
        "GET,  /api/v1/orders",
        "POST, /api/v1/products",
        "POST, /api/v1/orders",
        "POST, /api/v1/orders/0195f2a4-7d1e-7c3a-9a55-3f1d2c4b5a69/cancel"
    })
    void everyApiEndpointRequiresABearerToken(String method, String path) {
        var response = mvc.perform(request(HttpMethod.valueOf(method), path));

        assertThat(response)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        // RFC 6750 challenge; Spring Security also advertises RFC 9728 resource metadata in it.
        assertThat(response.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
        assertThat(response).bodyJson().extractingPath("$.type").isEqualTo("urn:orderflow:problem:unauthorized");
    }

    @Test
    void readScopeCanQueryButNotChangeAnything() {
        assertThat(mvc.get().uri("/api/v1/products").with(token(READ))).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/orders").with(token(READ))).hasStatusOk();

        var write = mvc.post()
                .uri("/api/v1/products")
                .with(token(READ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\": \"AUTH-READ\", \"name\": \"Nope\", \"onHandQuantity\": 1}");
        var cancel = mvc.post().uri("/api/v1/orders/{id}/cancel", UUID.randomUUID()).with(token(READ));

        assertThat(write)
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:orderflow:problem:forbidden");
        assertThat(cancel).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void operateScopeCanChangeButOnlyReadScopeCanQuery() {
        var write = mvc.post()
                .uri("/api/v1/products")
                .with(token(OPERATE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\": \"AUTH-OPERATE\", \"name\": \"Allowed\", \"onHandQuantity\": 1}");

        assertThat(write).hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get().uri("/api/v1/products/AUTH-OPERATE").with(token(OPERATE)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void apiDocumentationIsPublic() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
    }

    @Test
    void openApiDocumentsTheBearerScheme() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .bodyJson()
                .extractingPath("$.components.securitySchemes.bearer-jwt.scheme")
                .isEqualTo("bearer");
    }

    private static JwtRequestPostProcessor token(String... authorities) {
        return jwt().authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(GrantedAuthority[]::new));
    }
}

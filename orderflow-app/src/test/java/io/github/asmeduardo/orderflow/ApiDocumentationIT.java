package io.github.asmeduardo.orderflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.orderflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class ApiDocumentationIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void publishesTheOpenApiContract() {
        var docs = mvc.get().uri("/v3/api-docs");

        assertThat(docs).hasStatusOk().bodyJson().extractingPath("$.info.title").isEqualTo("OrderFlow API");
        assertThat(docs).bodyJson().hasPath("$.paths['/api/v1/orders'].post");
        assertThat(docs).bodyJson().hasPath("$.paths['/api/v1/orders/{id}/cancel'].post");
        assertThat(docs).bodyJson().hasPath("$.paths['/api/v1/products/{sku}'].get");
        assertThat(docs)
                .bodyJson()
                .extractingPath("$.paths['/api/v1/orders'].post.parameters[0].name")
                .isEqualTo("Idempotency-Key");
    }

    @Test
    void servesSwaggerUi() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
    }
}

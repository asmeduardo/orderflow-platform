package io.github.asmeduardo.orderflow.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Request validation only; authorization is covered by {@code ApiAuthorizationIT}. */
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ProductService products;

    @Test
    void reportsEveryInvalidFieldInOneValidationProblem() {
        var response = mvc.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku": "lower case!", "name": " ", "onHandQuantity": -1}
                        """);

        assertThat(response)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"type": "urn:orderflow:problem:invalid-request", "status": 400,
                         "errors": [
                           {"field": "name"}, {"field": "onHandQuantity"}, {"field": "sku"}
                         ]}
                        """);
        verifyNoInteractions(products);
    }

    @Test
    void rejectsMalformedJsonWithoutLeakingParserDetails() {
        var response = mvc.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\": ");

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
                        {"type": "urn:orderflow:problem:malformed-request", "status": 400,
                         "detail": "The request body could not be read."}
                        """);
    }
}

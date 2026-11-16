package io.github.asmeduardo.orderflow.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class ProductApiIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void registersProductAndExposesItsStockLevels() {
        var created = mvc.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku": "MUG-BLUE-01", "name": "Blue mug", "onHandQuantity": 12}
                        """)
                .exchange();

        assertThat(created)
                .hasStatus(HttpStatus.CREATED)
                .headers()
                .hasValue("Location", "/api/v1/products/MUG-BLUE-01");

        assertThat(mvc.get().uri("/api/v1/products/MUG-BLUE-01"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"sku": "MUG-BLUE-01", "name": "Blue mug",
                         "onHandQuantity": 12, "reservedQuantity": 0, "availableQuantity": 12}
                        """);
    }

    @Test
    void creationResponseShowsTheTimestampsThatWereStored() throws Exception {
        for (var sku : new String[] {"TIME-A", "TIME-B", "TIME-C"}) {
            var created = mvc.post()
                    .uri("/api/v1/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\": \"%s\", \"name\": \"Clock check\", \"onHandQuantity\": 1}".formatted(sku))
                    .exchange();
            String createdAt = JsonPath.read(created.getResponse().getContentAsString(), "$.createdAt");

            assertThat(mvc.get().uri("/api/v1/products/" + sku))
                    .bodyJson()
                    .extractingPath("$.createdAt")
                    .isEqualTo(createdAt);
        }
    }

    @Test
    void rejectsDuplicateSkuWithProblemDetail() {
        var body = """
                {"sku": "TEE-BLACK-M", "name": "Black tee M", "onHandQuantity": 3}
                """;
        mvc.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        var duplicate = mvc.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);

        assertThat(duplicate)
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"type": "urn:orderflow:problem:duplicate-sku", "status": 409,
                         "instance": "/api/v1/products"}
                        """);
    }

    @Test
    void returnsNotFoundProblemForUnknownSku() {
        assertThat(mvc.get().uri("/api/v1/products/UNKNOWN-SKU"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"type": "urn:orderflow:problem:product-not-found", "status": 404}
                        """);
    }

    @Test
    void listsProductsPageBySku() {
        for (var sku : new String[] {"LIST-C", "LIST-A", "LIST-B"}) {
            mvc.post()
                    .uri("/api/v1/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\": \"%s\", \"name\": \"Item %s\", \"onHandQuantity\": 1}".formatted(sku, sku))
                    .exchange();
        }

        assertThat(mvc.get().uri("/api/v1/products?skuPrefix=LIST-&size=2"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"content": [{"sku": "LIST-A"}, {"sku": "LIST-B"}],
                         "page": 0, "size": 2, "totalElements": 3, "totalPages": 2}
                        """);
    }
}

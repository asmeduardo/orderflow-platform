package io.github.asmeduardo.orderflow.inventory.web;

import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.inventory.application.ProductView;
import io.github.asmeduardo.orderflow.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product registration and stock levels")
public class ProductController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    @Operation(summary = "Register a product with its initial on-hand stock")
    @ApiResponse(responseCode = "201", description = "Product registered")
    @ApiResponse(
            responseCode = "409",
            description = "SKU already registered",
            content = @Content(mediaType = PROBLEM_JSON))
    public ResponseEntity<ProductView> register(@Valid @RequestBody RegisterProductRequest request) {
        var product = products.register(request.sku(), request.name().strip(), request.onHandQuantity());
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.sku()))
                .body(product);
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Get a product and its on-hand, reserved and available quantities")
    @ApiResponse(responseCode = "404", description = "Unknown SKU", content = @Content(mediaType = PROBLEM_JSON))
    public ProductView get(@PathVariable String sku) {
        return products.get(sku);
    }

    @GetMapping
    @Operation(summary = "List products ordered by SKU")
    public PageResponse<ProductView> list(
            @RequestParam(required = false) String skuPrefix,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(products.list(skuPrefix, page, size));
    }
}

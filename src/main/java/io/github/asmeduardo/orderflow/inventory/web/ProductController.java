package io.github.asmeduardo.orderflow.inventory.web;

import io.github.asmeduardo.orderflow.inventory.application.ProductService;
import io.github.asmeduardo.orderflow.inventory.application.ProductView;
import io.github.asmeduardo.orderflow.shared.web.PageResponse;
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
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    public ResponseEntity<ProductView> register(@Valid @RequestBody RegisterProductRequest request) {
        var product = products.register(request.sku(), request.name().strip(), request.onHandQuantity());
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.sku()))
                .body(product);
    }

    @GetMapping("/{sku}")
    public ProductView get(@PathVariable String sku) {
        return products.get(sku);
    }

    @GetMapping
    public PageResponse<ProductView> list(
            @RequestParam(required = false) String skuPrefix,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(products.list(skuPrefix, page, size));
    }
}

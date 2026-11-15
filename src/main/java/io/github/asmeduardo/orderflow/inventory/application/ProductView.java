package io.github.asmeduardo.orderflow.inventory.application;

import io.github.asmeduardo.orderflow.inventory.domain.Product;
import java.time.Instant;

public record ProductView(
        String sku,
        String name,
        int onHandQuantity,
        int reservedQuantity,
        int availableQuantity,
        Instant createdAt,
        Instant updatedAt) {

    static ProductView of(Product product) {
        return new ProductView(
                product.getSku(),
                product.getName(),
                product.getOnHandQuantity(),
                product.getReservedQuantity(),
                product.availableQuantity(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}

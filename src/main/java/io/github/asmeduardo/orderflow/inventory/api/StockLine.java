package io.github.asmeduardo.orderflow.inventory.api;

import java.util.Objects;

/** A quantity of one product that an order needs. */
public record StockLine(String sku, int quantity) {

    public StockLine {
        Objects.requireNonNull(sku, "sku");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}

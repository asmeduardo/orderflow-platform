package io.github.asmeduardo.orderflow.inventory.application;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String sku) {
        super("No product with SKU " + sku);
    }
}

package io.github.asmeduardo.orderflow.inventory.application;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("A product with SKU " + sku + " already exists");
    }
}

package io.github.asmeduardo.orderflow.inventory.api;

/** Why a line could not be reserved, with the availability observed while deciding. */
public record Shortage(String sku, Reason reason, int requested, int available) {

    public enum Reason {
        UNKNOWN_PRODUCT,
        INSUFFICIENT_STOCK
    }
}

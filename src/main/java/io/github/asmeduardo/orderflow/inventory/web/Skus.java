package io.github.asmeduardo.orderflow.inventory.web;

final class Skus {

    static final String PATTERN = "[A-Z0-9][A-Z0-9-]{1,63}";
    static final String MESSAGE = "must be 2-64 uppercase letters, digits or hyphens";

    private Skus() {}
}

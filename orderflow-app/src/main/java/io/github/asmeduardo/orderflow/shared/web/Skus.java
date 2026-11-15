package io.github.asmeduardo.orderflow.shared.web;

/** Format shared by every module that accepts a SKU. */
public final class Skus {

    public static final String PATTERN = "[A-Z0-9][A-Z0-9-]{1,63}";
    public static final String MESSAGE = "must be 2-64 uppercase letters, digits or hyphens";

    private Skus() {}
}

package io.github.asmeduardo.orderflow.orders.domain;

/** The order request is well formed but breaks a business rule. */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}

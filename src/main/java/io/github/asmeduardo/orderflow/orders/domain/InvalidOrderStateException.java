package io.github.asmeduardo.orderflow.orders.domain;

/** The requested transition is not allowed from the current status of the order. */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}

package io.github.asmeduardo.orderflow.orders.application;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID id) {
        super("No order with id " + id);
    }
}

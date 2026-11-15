package io.github.asmeduardo.orderflow.orders.application;

/** The idempotency key was already used for a request with a different payload. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("The Idempotency-Key was already used for a different request");
    }
}

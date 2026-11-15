package io.github.asmeduardo.orderflow.orders.web;

import io.github.asmeduardo.orderflow.orders.application.IdempotencyKeyReusedException;
import io.github.asmeduardo.orderflow.orders.application.OrderNotFoundException;
import io.github.asmeduardo.orderflow.orders.domain.InvalidOrderException;
import io.github.asmeduardo.orderflow.orders.domain.InvalidOrderStateException;
import io.github.asmeduardo.orderflow.shared.web.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = OrdersProblemHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class OrdersProblemHandler {

    @ExceptionHandler
    ProblemDetail orderNotFound(OrderNotFoundException exception) {
        return Problems.of(HttpStatus.NOT_FOUND, "order-not-found", "Order not found", exception.getMessage());
    }

    @ExceptionHandler
    ProblemDetail invalidOrder(InvalidOrderException exception) {
        return Problems.of(HttpStatus.BAD_REQUEST, "invalid-order", "Invalid order", exception.getMessage());
    }

    @ExceptionHandler
    ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException exception) {
        return Problems.of(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "idempotency-key-reused",
                "Idempotency key reused",
                exception.getMessage());
    }

    @ExceptionHandler
    ProblemDetail invalidOrderState(InvalidOrderStateException exception) {
        return Problems.of(
                HttpStatus.CONFLICT, "invalid-order-state", "Invalid order state", exception.getMessage());
    }
}

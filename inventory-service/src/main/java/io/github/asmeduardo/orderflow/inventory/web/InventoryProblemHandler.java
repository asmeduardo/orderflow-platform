package io.github.asmeduardo.orderflow.inventory.web;

import io.github.asmeduardo.orderflow.inventory.application.DuplicateSkuException;
import io.github.asmeduardo.orderflow.inventory.application.ProductNotFoundException;
import io.github.asmeduardo.orderflow.shared.web.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = InventoryProblemHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class InventoryProblemHandler {

    @ExceptionHandler
    ProblemDetail productNotFound(ProductNotFoundException exception) {
        return Problems.of(HttpStatus.NOT_FOUND, "product-not-found", "Product not found", exception.getMessage());
    }

    @ExceptionHandler
    ProblemDetail duplicateSku(DuplicateSkuException exception) {
        return Problems.of(HttpStatus.CONFLICT, "duplicate-sku", "SKU already registered", exception.getMessage());
    }
}

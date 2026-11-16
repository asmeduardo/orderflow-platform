package io.github.asmeduardo.orderflow.inventory.web;

import io.github.asmeduardo.orderflow.shared.web.Skus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

record RegisterProductRequest(
        @NotNull @Pattern(regexp = Skus.PATTERN, message = Skus.MESSAGE)
        String sku,

        @NotBlank @Size(max = 200) String name,
        @NotNull @PositiveOrZero Integer onHandQuantity) {}

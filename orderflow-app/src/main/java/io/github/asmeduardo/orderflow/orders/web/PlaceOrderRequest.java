package io.github.asmeduardo.orderflow.orders.web;

import io.github.asmeduardo.orderflow.orders.application.PlaceOrderCommand;
import io.github.asmeduardo.orderflow.orders.domain.OrderLine;
import io.github.asmeduardo.orderflow.shared.web.Skus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

record PlaceOrderRequest(
        @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}", message = "must be 2-32 uppercase letters, digits or underscores")
                String channel,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 currency code") String currency,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {

    record Item(
            @NotNull @Pattern(regexp = Skus.PATTERN, message = Skus.MESSAGE) String sku,
            @NotNull @Positive @Max(10_000) Integer quantity,
            @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitPrice) {}

    PlaceOrderCommand toCommand() {
        var lines = items.stream()
                .map(item -> new OrderLine(item.sku(), item.quantity(), item.unitPrice()))
                .toList();
        return new PlaceOrderCommand(channel, currency, lines);
    }
}

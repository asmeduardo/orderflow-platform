package io.github.asmeduardo.orderflow.orders.application;

import io.github.asmeduardo.orderflow.orders.domain.Order;
import io.github.asmeduardo.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Order header for listings; it never touches the items, so a page costs a single query. */
public record OrderSummary(
        UUID id,
        OrderStatus status,
        String channel,
        String currency,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt) {

    static OrderSummary of(Order order) {
        return new OrderSummary(
                order.getId(),
                order.getStatus(),
                order.getChannel(),
                order.getCurrencyCode(),
                order.total().amount(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}

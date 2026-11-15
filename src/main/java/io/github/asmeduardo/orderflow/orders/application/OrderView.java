package io.github.asmeduardo.orderflow.orders.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.asmeduardo.orderflow.orders.domain.Order;
import io.github.asmeduardo.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(
        UUID id,
        OrderStatus status,
        String channel,
        String currency,
        List<Item> items,
        BigDecimal totalAmount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String rejectionReason,
        Instant createdAt,
        Instant updatedAt) {

    public record Item(String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}

    static OrderView of(Order order) {
        var items = order.getItems().stream()
                .map(item -> new Item(
                        item.getSku(),
                        item.getQuantity(),
                        item.unitPrice().amount(),
                        item.lineTotal().amount()))
                .toList();
        return new OrderView(
                order.getId(),
                order.getStatus(),
                order.getChannel(),
                order.getCurrencyCode(),
                items,
                order.total().amount(),
                order.getRejectionReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}

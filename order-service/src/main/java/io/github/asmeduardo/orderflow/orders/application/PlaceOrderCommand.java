package io.github.asmeduardo.orderflow.orders.application;

import io.github.asmeduardo.orderflow.orders.domain.OrderLine;
import java.util.List;

public record PlaceOrderCommand(String channel, String currency, List<OrderLine> lines) {

    public PlaceOrderCommand {
        lines = List.copyOf(lines);
    }
}

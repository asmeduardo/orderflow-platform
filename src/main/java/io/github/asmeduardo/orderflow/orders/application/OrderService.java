package io.github.asmeduardo.orderflow.orders.application;

import io.github.asmeduardo.orderflow.inventory.api.ReservationOutcome;
import io.github.asmeduardo.orderflow.inventory.api.Shortage;
import io.github.asmeduardo.orderflow.inventory.api.StockLine;
import io.github.asmeduardo.orderflow.inventory.api.StockReservations;
import io.github.asmeduardo.orderflow.orders.domain.InvalidOrderException;
import io.github.asmeduardo.orderflow.orders.domain.Order;
import io.github.asmeduardo.orderflow.orders.domain.OrderRepository;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final int MAX_REASON_LENGTH = 500;

    private final OrderRepository orders;
    private final StockReservations stock;
    private final Clock clock;

    public OrderService(OrderRepository orders, StockReservations stock, Clock clock) {
        this.orders = orders;
        this.stock = stock;
        this.clock = clock;
    }

    /**
     * Records the order and reserves its stock in the same local transaction. The order is
     * confirmed when every line is reserved and rejected, with the shortages as reason, otherwise.
     */
    @Transactional
    public OrderView place(PlaceOrderCommand command) {
        var order = orders.saveAndFlush(
                Order.place(command.channel(), currencyOf(command.currency()), command.lines(), clock.instant()));
        var lines = order.getItems().stream()
                .map(item -> new StockLine(item.getSku(), item.getQuantity()))
                .toList();
        switch (stock.reserve(order.getId(), lines)) {
            case ReservationOutcome.Reserved reserved -> order.confirm(clock.instant());
            case ReservationOutcome.Rejected rejected -> order.reject(describe(rejected.shortages()), clock.instant());
        }
        return OrderView.of(order);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID id) {
        return orders.findWithItemsById(id).map(OrderView::of).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private static Currency currencyOf(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderException("Unknown currency " + code);
        }
    }

    private static String describe(List<Shortage> shortages) {
        var reason = shortages.stream()
                .map(shortage -> switch (shortage.reason()) {
                    case UNKNOWN_PRODUCT -> "unknown product " + shortage.sku();
                    case INSUFFICIENT_STOCK ->
                        "insufficient stock for %s (requested %d, available %d)"
                                .formatted(shortage.sku(), shortage.requested(), shortage.available());
                })
                .collect(Collectors.joining("; "));
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH - 3) + "...";
    }
}

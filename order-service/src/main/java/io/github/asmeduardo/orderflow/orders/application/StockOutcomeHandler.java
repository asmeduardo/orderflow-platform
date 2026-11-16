package io.github.asmeduardo.orderflow.orders.application;

import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.StockRejectedV1;
import io.github.asmeduardo.orderflow.events.StockReservedV1;
import io.github.asmeduardo.orderflow.orders.domain.Order;
import io.github.asmeduardo.orderflow.orders.domain.OrderRepository;
import io.github.asmeduardo.orderflow.shared.messaging.ProcessedEvents;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the outcome of a stock reservation to the order. Only a pending order changes: an order
 * cancelled meanwhile stays cancelled, and its OrderCancelled event releases the stock.
 */
@Service
public class StockOutcomeHandler {

    private static final Logger log = LoggerFactory.getLogger(StockOutcomeHandler.class);
    private static final int MAX_REASON_LENGTH = 500;

    private final OrderRepository orders;
    private final ProcessedEvents processed;
    private final Clock clock;

    StockOutcomeHandler(
            OrderRepository orders, @Qualifier("ordersProcessedEvents") ProcessedEvents processed, Clock clock) {
        this.orders = orders;
        this.processed = processed;
        this.clock = clock;
    }

    @Transactional
    public void stockReserved(UUID eventId, StockReservedV1 event) {
        if (processed.markProcessed(eventId, EventTypes.STOCK_RESERVED.name())) {
            applyToPendingOrder(event.orderId(), order -> order.confirm(clock.instant()));
        }
    }

    @Transactional
    public void stockRejected(UUID eventId, StockRejectedV1 event) {
        if (processed.markProcessed(eventId, EventTypes.STOCK_REJECTED.name())) {
            applyToPendingOrder(event.orderId(), order -> order.reject(describe(event), clock.instant()));
        }
    }

    private void applyToPendingOrder(UUID orderId, Consumer<Order> change) {
        orders.findById(orderId)
                .ifPresentOrElse(
                        order -> {
                            if (order.isPending()) {
                                change.accept(order);
                            } else {
                                log.info("Order {} is {}; reservation outcome ignored", orderId, order.getStatus());
                            }
                        },
                        () -> log.warn("Reservation outcome for unknown order {} ignored", orderId));
    }

    private static String describe(StockRejectedV1 event) {
        var reason = event.shortages().stream()
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

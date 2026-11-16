package io.github.asmeduardo.orderflow.inventory.application;

import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.OrderCancelledV1;
import io.github.asmeduardo.orderflow.events.OrderPlacedV1;
import io.github.asmeduardo.orderflow.events.StockRejectedV1;
import io.github.asmeduardo.orderflow.events.StockReservedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.shared.messaging.ProcessedEvents;
import io.github.asmeduardo.orderflow.shared.messaging.TransactionalOutbox;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to order events. Each handling runs in one transaction that marks the event as processed,
 * changes the stock and records the outcome event, so a redelivery can neither reserve twice nor
 * publish a second outcome.
 */
@Service
public class OrderEventsHandler {

    private final StockReservationService reservations;
    private final ProcessedEvents processed;
    private final TransactionalOutbox outbox;

    OrderEventsHandler(
            StockReservationService reservations,
            @Qualifier("inventoryProcessedEvents") ProcessedEvents processed,
            @Qualifier("inventoryOutbox") TransactionalOutbox outbox) {
        this.reservations = reservations;
        this.processed = processed;
        this.outbox = outbox;
    }

    @Transactional
    public void orderPlaced(UUID eventId, OrderPlacedV1 order) {
        if (!processed.markProcessed(eventId, EventTypes.ORDER_PLACED.name())) {
            return;
        }
        var lines = order.lines().stream()
                .map(line -> new StockLine(line.sku(), line.quantity()))
                .toList();
        var key = order.orderId().toString();
        switch (reservations.reserve(order.orderId(), lines)) {
            case ReservationOutcome.Reserved _ ->
                outbox.append(
                        Topics.INVENTORY_EVENTS, EventTypes.STOCK_RESERVED, key, new StockReservedV1(order.orderId()));
            case ReservationOutcome.Rejected rejected ->
                outbox.append(
                        Topics.INVENTORY_EVENTS,
                        EventTypes.STOCK_REJECTED,
                        key,
                        new StockRejectedV1(
                                order.orderId(),
                                rejected.shortages().stream()
                                        .map(OrderEventsHandler::toContract)
                                        .toList()));
        }
    }

    @Transactional
    public void orderCancelled(UUID eventId, OrderCancelledV1 cancellation) {
        if (!processed.markProcessed(eventId, EventTypes.ORDER_CANCELLED.name())) {
            return;
        }
        reservations.release(cancellation.orderId());
    }

    private static StockRejectedV1.Shortage toContract(Shortage shortage) {
        var reason = switch (shortage.reason()) {
            case UNKNOWN_PRODUCT -> StockRejectedV1.Reason.UNKNOWN_PRODUCT;
            case INSUFFICIENT_STOCK -> StockRejectedV1.Reason.INSUFFICIENT_STOCK;
        };
        return new StockRejectedV1.Shortage(shortage.sku(), reason, shortage.requested(), shortage.available());
    }
}

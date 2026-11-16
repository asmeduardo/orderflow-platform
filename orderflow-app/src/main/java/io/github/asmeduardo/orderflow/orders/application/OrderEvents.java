package io.github.asmeduardo.orderflow.orders.application;

import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.OrderCancelledV1;
import io.github.asmeduardo.orderflow.events.OrderPlacedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.orders.domain.Order;
import io.github.asmeduardo.orderflow.shared.messaging.TransactionalOutbox;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Translates order changes into the published event contracts, through the orders outbox. */
@Component
class OrderEvents {

    private final TransactionalOutbox outbox;

    OrderEvents(@Qualifier("ordersOutbox") TransactionalOutbox outbox) {
        this.outbox = outbox;
    }

    void placed(Order order) {
        var lines = order.getItems().stream()
                .map(item -> new OrderPlacedV1.Line(item.getSku(), item.getQuantity()))
                .toList();
        outbox.append(
                Topics.ORDER_EVENTS,
                EventTypes.ORDER_PLACED,
                order.getId().toString(),
                new OrderPlacedV1(order.getId(), lines));
    }

    void cancelled(Order order) {
        outbox.append(
                Topics.ORDER_EVENTS,
                EventTypes.ORDER_CANCELLED,
                order.getId().toString(),
                new OrderCancelledV1(order.getId()));
    }
}

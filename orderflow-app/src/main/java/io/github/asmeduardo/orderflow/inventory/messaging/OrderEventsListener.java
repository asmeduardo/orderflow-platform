package io.github.asmeduardo.orderflow.inventory.messaging;

import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.OrderCancelledV1;
import io.github.asmeduardo.orderflow.events.OrderPlacedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.inventory.application.OrderEventsHandler;
import io.github.asmeduardo.orderflow.shared.messaging.EventReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka adapter of the inventory module: reads order events and hands them to the application. */
@Component
class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final EventReader reader;
    private final OrderEventsHandler handler;

    OrderEventsListener(EventReader reader, OrderEventsHandler handler) {
        this.reader = reader;
        this.handler = handler;
    }

    @KafkaListener(id = "inventory-order-events", topics = Topics.ORDER_EVENTS, groupId = "inventory")
    void on(String value) {
        var event = reader.envelope(value);
        if (event.is(EventTypes.ORDER_PLACED)) {
            handler.orderPlaced(event.eventId(), reader.payload(event, EventTypes.ORDER_PLACED, OrderPlacedV1.class));
        } else if (event.is(EventTypes.ORDER_CANCELLED)) {
            handler.orderCancelled(
                    event.eventId(), reader.payload(event, EventTypes.ORDER_CANCELLED, OrderCancelledV1.class));
        } else {
            log.debug("Ignoring {} v{} {}", event.eventType(), event.eventVersion(), event.eventId());
        }
    }
}

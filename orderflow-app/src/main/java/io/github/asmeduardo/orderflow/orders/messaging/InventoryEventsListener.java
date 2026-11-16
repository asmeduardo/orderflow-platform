package io.github.asmeduardo.orderflow.orders.messaging;

import io.github.asmeduardo.orderflow.events.EventTypes;
import io.github.asmeduardo.orderflow.events.StockRejectedV1;
import io.github.asmeduardo.orderflow.events.StockReservedV1;
import io.github.asmeduardo.orderflow.events.Topics;
import io.github.asmeduardo.orderflow.orders.application.StockOutcomeHandler;
import io.github.asmeduardo.orderflow.shared.messaging.EventReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka adapter of the orders module: reads reservation outcomes published by the inventory. */
@Component
class InventoryEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsListener.class);

    private final EventReader reader;
    private final StockOutcomeHandler handler;

    InventoryEventsListener(EventReader reader, StockOutcomeHandler handler) {
        this.reader = reader;
        this.handler = handler;
    }

    @KafkaListener(id = "orders-inventory-events", topics = Topics.INVENTORY_EVENTS, groupId = "orders")
    void on(String value) {
        var event = reader.envelope(value);
        if (event.is(EventTypes.STOCK_RESERVED)) {
            handler.stockReserved(
                    event.eventId(), reader.payload(event, EventTypes.STOCK_RESERVED, StockReservedV1.class));
        } else if (event.is(EventTypes.STOCK_REJECTED)) {
            handler.stockRejected(
                    event.eventId(), reader.payload(event, EventTypes.STOCK_REJECTED, StockRejectedV1.class));
        } else {
            log.debug("Ignoring {} v{} {}", event.eventType(), event.eventVersion(), event.eventId());
        }
    }
}

package io.github.asmeduardo.orderflow.events;

/** Every event type published by the platform, with its current schema version. */
public final class EventTypes {

    /** Published by orders on {@link Topics#ORDER_EVENTS}; payload {@link OrderPlacedV1}. */
    public static final EventType ORDER_PLACED = new EventType("OrderPlaced", 1);

    /** Published by orders on {@link Topics#ORDER_EVENTS}; payload {@link OrderCancelledV1}. */
    public static final EventType ORDER_CANCELLED = new EventType("OrderCancelled", 1);

    /** Published by inventory on {@link Topics#INVENTORY_EVENTS}; payload {@link StockReservedV1}. */
    public static final EventType STOCK_RESERVED = new EventType("StockReserved", 1);

    /** Published by inventory on {@link Topics#INVENTORY_EVENTS}; payload {@link StockRejectedV1}. */
    public static final EventType STOCK_REJECTED = new EventType("StockRejected", 1);

    private EventTypes() {}
}

package io.github.asmeduardo.orderflow.events;

/** Kafka topics; records are keyed by order id. */
public final class Topics {

    public static final String ORDER_EVENTS = "orderflow.order-events";
    public static final String INVENTORY_EVENTS = "orderflow.inventory-events";

    /** Suffix of the dead-letter topic that receives what a consumer could not process. */
    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    private Topics() {}
}

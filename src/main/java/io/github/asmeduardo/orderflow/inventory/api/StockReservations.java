package io.github.asmeduardo.orderflow.inventory.api;

import java.util.List;
import java.util.UUID;

/**
 * Public entry point of the inventory module for reserving stock. It is the only inventory type
 * other modules may depend on.
 */
public interface StockReservations {

    /**
     * Reserves all lines or none. Calling it again for an order that already holds a reservation
     * returns {@link ReservationOutcome.Reserved} without reserving a second time.
     */
    ReservationOutcome reserve(UUID orderId, List<StockLine> lines);

    /** Returns the stock held by the order. Releasing an order without reservations does nothing. */
    void release(UUID orderId);
}

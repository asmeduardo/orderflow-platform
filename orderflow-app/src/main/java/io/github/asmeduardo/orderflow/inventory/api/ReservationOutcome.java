package io.github.asmeduardo.orderflow.inventory.api;

import java.util.List;

public sealed interface ReservationOutcome {

    /** Every line of the order is now reserved. */
    record Reserved() implements ReservationOutcome {}

    /** Nothing was reserved because at least one line could not be fulfilled. */
    record Rejected(List<Shortage> shortages) implements ReservationOutcome {

        public Rejected {
            shortages = List.copyOf(shortages);
        }
    }
}

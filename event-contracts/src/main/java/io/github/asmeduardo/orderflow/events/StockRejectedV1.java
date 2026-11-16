package io.github.asmeduardo.orderflow.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/** Nothing was reserved because at least one line could not be fulfilled. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockRejectedV1(UUID orderId, List<Shortage> shortages) {

    public StockRejectedV1 {
        shortages = List.copyOf(shortages);
    }

    public enum Reason {
        UNKNOWN_PRODUCT,
        INSUFFICIENT_STOCK
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shortage(String sku, Reason reason, int requested, int available) {}
}

package io.github.asmeduardo.orderflow.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/** An order was recorded and needs its stock reserved. Carries only what the inventory needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedV1(UUID orderId, List<Line> lines) {

    public OrderPlacedV1 {
        lines = List.copyOf(lines);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(String sku, int quantity) {}
}

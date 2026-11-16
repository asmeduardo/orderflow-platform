package io.github.asmeduardo.orderflow.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Every line of the order is reserved. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockReservedV1(UUID orderId) {}

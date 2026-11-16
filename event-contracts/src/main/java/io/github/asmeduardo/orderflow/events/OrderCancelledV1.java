package io.github.asmeduardo.orderflow.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** An order was cancelled; any stock held for it must be released. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledV1(UUID orderId) {}

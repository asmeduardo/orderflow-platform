package io.github.asmeduardo.orderflow.orders.application;

/** The order a request produced and whether it was created by an earlier request with the same key. */
public record PlacementResult(OrderView order, boolean replayed) {}

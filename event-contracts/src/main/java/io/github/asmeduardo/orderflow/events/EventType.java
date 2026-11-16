package io.github.asmeduardo.orderflow.events;

/** Name and schema version of an event; a breaking change to a payload gets a new version. */
public record EventType(String name, int version) {}

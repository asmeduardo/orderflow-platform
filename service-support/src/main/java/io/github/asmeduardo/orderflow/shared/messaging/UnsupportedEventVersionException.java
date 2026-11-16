package io.github.asmeduardo.orderflow.shared.messaging;

/**
 * The event type is known but not in this schema version. Retrying cannot help, so the record goes
 * straight to the dead-letter topic until a consumer that understands the version is deployed.
 */
public class UnsupportedEventVersionException extends RuntimeException {

    public UnsupportedEventVersionException(String eventType, int version) {
        super("Unsupported version " + version + " of " + eventType);
    }
}

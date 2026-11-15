package io.github.asmeduardo.orderflow.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Remembers which order a client request created. The primary key makes a second insert with the
 * same key wait for, and then fail after, the first one, which is how concurrent retries are
 * detected.
 */
@Entity
@Table(name = "idempotency_key", schema = "orders")
public class IdempotencyKey implements Persistable<String> {

    @Id
    @Column(name = "idempotency_key", length = 100)
    private String key;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean persisted;

    protected IdempotencyKey() {}

    public IdempotencyKey(String key, String requestFingerprint, UUID orderId, Instant now) {
        this.key = Objects.requireNonNull(key, "key");
        this.requestFingerprint = Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public boolean matches(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }

    public UUID getOrderId() {
        return orderId;
    }

    @Override
    public String getId() {
        return key;
    }

    /** The key is assigned by the client, so Spring Data cannot infer novelty from a null id. */
    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }
}

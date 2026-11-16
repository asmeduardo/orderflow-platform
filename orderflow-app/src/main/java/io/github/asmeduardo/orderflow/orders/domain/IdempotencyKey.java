package io.github.asmeduardo.orderflow.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Remembers which order a client request created. Keys belong to the client that sent them, so two
 * clients may pick the same key without seeing each other's orders. The primary key makes a second
 * insert of the same client and key wait for, and then fail after, the first one, which is how
 * concurrent retries are detected.
 */
@Entity
@Table(name = "idempotency_key", schema = "orders")
public class IdempotencyKey implements Persistable<IdempotencyKey.Id> {

    /** A key is unique per client, identified by the subject of its access token. */
    @Embeddable
    public record Id(
            @Column(name = "client_id", length = 100) String clientId,
            @Column(name = "idempotency_key", length = 100) String key) {

        public Id {
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(key, "key");
        }
    }

    @EmbeddedId
    private Id id;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean persisted;

    protected IdempotencyKey() {}

    public IdempotencyKey(Id id, String requestFingerprint, UUID orderId, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
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
    public Id getId() {
        return id;
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

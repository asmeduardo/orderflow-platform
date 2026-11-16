package io.github.asmeduardo.orderflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Tombstone of an order the inventory saw being cancelled; it outlives any reservation. */
@Entity
@Table(name = "cancelled_order", schema = "inventory")
public class CancelledOrder implements Persistable<UUID> {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "cancelled_at", nullable = false, updatable = false)
    private Instant cancelledAt;

    protected CancelledOrder() {}

    public CancelledOrder(UUID orderId, Instant now) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.cancelledAt = Objects.requireNonNull(now, "now");
    }

    @Override
    public UUID getId() {
        return orderId;
    }

    /** Only ever inserted, after checking it does not exist yet. */
    @Override
    public boolean isNew() {
        return true;
    }
}

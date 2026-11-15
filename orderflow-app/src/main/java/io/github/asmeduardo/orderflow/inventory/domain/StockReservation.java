package io.github.asmeduardo.orderflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Units of one product held for one order until the order is fulfilled or cancelled. */
@Entity
@Table(name = "stock_reservation", schema = "inventory")
public class StockReservation {

    public enum Status {
        RESERVED,
        RELEASED
    }

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected StockReservation() {}

    public StockReservation(UUID orderId, Product product, int quantity, Instant now) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.product = Objects.requireNonNull(product, "product");
        this.quantity = quantity;
        this.status = Status.RESERVED;
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    public void markReleased(Instant now) {
        if (status != Status.RESERVED) {
            throw new IllegalStateException("Reservation " + id + " is already " + status);
        }
        status = Status.RELEASED;
        releasedAt = now;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public Status getStatus() {
        return status;
    }
}

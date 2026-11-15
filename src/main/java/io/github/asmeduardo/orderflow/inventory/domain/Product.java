package io.github.asmeduardo.orderflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

/**
 * A sellable item and its stock levels. {@code onHandQuantity} is the physical stock; the part of
 * it already promised to orders is {@code reservedQuantity}. The database enforces that the
 * reserved quantity never exceeds what is on hand.
 */
@Entity
@Table(name = "product", schema = "inventory")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "on_hand_quantity", nullable = false)
    private int onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public Product(String sku, String name, int onHandQuantity, Instant now) {
        if (onHandQuantity < 0) {
            throw new IllegalArgumentException("On-hand quantity must not be negative");
        }
        this.sku = Objects.requireNonNull(sku, "sku");
        this.name = Objects.requireNonNull(name, "name");
        this.onHandQuantity = onHandQuantity;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public int availableQuantity() {
        return onHandQuantity - reservedQuantity;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public int getOnHandQuantity() {
        return onHandQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

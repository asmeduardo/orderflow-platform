package io.github.asmeduardo.orderflow.orders.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A sales order received from one channel. Its status records what is known about its stock. */
@Entity(name = "SalesOrder")
@Table(name = "sales_order", schema = "orders")
public class Order {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 32)
    private String channel;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    private Order(String channel, Currency currency, Instant now) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.currency = currency.getCurrencyCode();
        this.status = OrderStatus.PENDING;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static Order place(String channel, Currency currency, List<OrderLine> lines, Instant now) {
        if (lines.isEmpty()) {
            throw new InvalidOrderException("An order needs at least one item");
        }
        var seen = new HashSet<String>();
        for (var line : lines) {
            if (!seen.add(line.sku())) {
                throw new InvalidOrderException("SKU " + line.sku() + " appears more than once");
            }
        }
        var order = new Order(channel, currency, now);
        var total = Money.zero(currency);
        int lineNumber = 1;
        for (var line : lines) {
            var unitPrice = priceIn(currency, line);
            order.items.add(new OrderItem(order, lineNumber++, line.sku(), line.quantity(), unitPrice));
            total = total.plus(unitPrice.times(line.quantity()));
        }
        order.totalAmount = total.amount();
        return order;
    }

    public void confirm(Instant now) {
        requireStatus(OrderStatus.PENDING, "confirm");
        status = OrderStatus.CONFIRMED;
        updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection needs a reason");
        }
        requireStatus(OrderStatus.PENDING, "reject");
        status = OrderStatus.REJECTED;
        rejectionReason = reason;
        updatedAt = now;
    }

    public Money total() {
        return new Money(totalAmount, currency());
    }

    Currency currency() {
        return Currency.getInstance(currency);
    }

    private void requireStatus(OrderStatus expected, String action) {
        if (status != expected) {
            throw new InvalidOrderStateException("Cannot " + action + " order " + id + " while it is " + status);
        }
    }

    private static Money priceIn(Currency currency, OrderLine line) {
        try {
            return new Money(line.unitPrice(), currency);
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderException("Invalid unit price for SKU " + line.sku() + ": " + e.getMessage());
        }
    }

    public UUID getId() {
        return id;
    }

    public String getChannel() {
        return channel;
    }

    public String getCurrencyCode() {
        return currency;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

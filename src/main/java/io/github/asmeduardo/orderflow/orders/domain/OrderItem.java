package io.github.asmeduardo.orderflow.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "sales_order_item", schema = "orders")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Column(nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    protected OrderItem() {}

    OrderItem(Order order, int lineNumber, String sku, int quantity, Money unitPrice) {
        this.order = order;
        this.lineNumber = lineNumber;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice.amount();
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money unitPrice() {
        return new Money(unitPrice, order.currency());
    }

    public Money lineTotal() {
        return unitPrice().times(quantity);
    }
}

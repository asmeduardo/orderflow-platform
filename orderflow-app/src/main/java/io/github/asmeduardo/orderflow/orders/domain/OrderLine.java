package io.github.asmeduardo.orderflow.orders.domain;

import java.math.BigDecimal;

/** What the sales channel asked for: a SKU, a quantity and the unit price it charged. */
public record OrderLine(String sku, int quantity, BigDecimal unitPrice) {}

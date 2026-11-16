package io.github.asmeduardo.orderflow.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An exact, non-negative amount in one currency. The amount is always kept at the scale of the
 * currency (two decimal places for BRL, none for JPY), so equal values are equal records.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new IllegalArgumentException(currency + " is not a monetary currency");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (amount.stripTrailingZeros().scale() > fractionDigits) {
            throw new IllegalArgumentException(
                    amount.toPlainString() + " has more decimal places than " + currency + " allows");
        }
        amount = amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add " + other.currency + " to " + currency);
        }
        return new Money(amount.add(other.amount), currency);
    }
}

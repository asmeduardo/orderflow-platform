package io.github.asmeduardo.orderflow.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Test
    void normalizesAmountToTheCurrencyScale() {
        assertThat(new Money(new BigDecimal("59.9"), BRL).amount())
                .isEqualByComparingTo("59.90")
                .hasScaleOf(2);
        assertThat(new Money(new BigDecimal("1500"), JPY).amount()).hasScaleOf(0);
    }

    @Test
    void rejectsMoreDecimalPlacesThanTheCurrencyAllows() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Money(new BigDecimal("10.005"), BRL));
        assertThatIllegalArgumentException().isThrownBy(() -> new Money(new BigDecimal("10.5"), JPY));
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Money(new BigDecimal("-0.01"), BRL));
    }

    @Test
    void multipliesAndAddsWithoutLosingPrecision() {
        var unitPrice = new Money(new BigDecimal("0.10"), BRL);

        var total = unitPrice.times(3).plus(new Money(new BigDecimal("0.20"), BRL));

        assertThat(total).isEqualTo(new Money(new BigDecimal("0.50"), BRL));
    }

    @Test
    void refusesToAddDifferentCurrencies() {
        var reais = new Money(BigDecimal.ONE, BRL);
        var dollars = new Money(BigDecimal.ONE, Currency.getInstance("USD"));

        assertThatIllegalArgumentException().isThrownBy(() -> reais.plus(dollars));
    }
}

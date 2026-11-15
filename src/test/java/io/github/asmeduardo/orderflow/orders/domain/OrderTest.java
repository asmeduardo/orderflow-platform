package io.github.asmeduardo.orderflow.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(5);

    @Test
    void newOrderIsPendingAndTotalsItsLines() {
        var order = Order.place(
                "WEB_STORE",
                BRL,
                List.of(new OrderLine("MUG-BLUE-01", 2, new BigDecimal("59.90")), new OrderLine("TEE-M", 1, new BigDecimal("0.20"))),
                NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.total()).isEqualTo(new Money(new BigDecimal("120.00"), BRL));
        assertThat(order.getItems()).extracting(OrderItem::getSku).containsExactly("MUG-BLUE-01", "TEE-M");
        assertThat(order.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void orderNeedsAtLeastOneLineAndEachSkuOnce() {
        assertThatThrownBy(() -> Order.place("WEB_STORE", BRL, List.of(), NOW))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> Order.place(
                        "WEB_STORE",
                        BRL,
                        List.of(new OrderLine("MUG", 1, BigDecimal.ONE), new OrderLine("MUG", 2, BigDecimal.ONE)),
                        NOW))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("MUG");
    }

    @Test
    void pricesMustFitTheOrderCurrency() {
        assertThatThrownBy(() -> Order.place(
                        "WEB_STORE",
                        Currency.getInstance("JPY"),
                        List.of(new OrderLine("MUG", 1, new BigDecimal("10.50"))),
                        NOW))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void pendingOrderCanBeConfirmed() {
        var order = pendingOrder();

        order.confirm(LATER);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void rejectedOrderKeepsTheReason() {
        var order = pendingOrder();

        order.reject("Insufficient stock for MUG", LATER);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("Insufficient stock for MUG");
    }

    @Test
    void decidedOrderCannotBeDecidedAgain() {
        var order = pendingOrder();
        order.reject("No stock", LATER);

        assertThatThrownBy(() -> order.confirm(LATER)).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void rejectionNeedsAReason() {
        assertThatIllegalArgumentException().isThrownBy(() -> pendingOrder().reject(" ", LATER));
    }

    private static Order pendingOrder() {
        return Order.place("WEB_STORE", BRL, List.of(new OrderLine("MUG", 1, BigDecimal.TEN)), NOW);
    }
}

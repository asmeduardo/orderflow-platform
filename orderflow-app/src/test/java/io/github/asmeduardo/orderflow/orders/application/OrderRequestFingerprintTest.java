package io.github.asmeduardo.orderflow.orders.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.orderflow.orders.domain.OrderLine;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderRequestFingerprintTest {

    private static final OrderLine MUG = new OrderLine("MUG", 2, new BigDecimal("59.90"));
    private static final OrderLine TEE = new OrderLine("TEE", 1, new BigDecimal("80"));

    @Test
    void ignoresItemOrderAndTrailingZeros() {
        var original = new PlaceOrderCommand("WEB_STORE", "BRL", List.of(MUG, TEE));
        var equivalent = new PlaceOrderCommand(
                "WEB_STORE",
                "BRL",
                List.of(new OrderLine("TEE", 1, new BigDecimal("80.00")), new OrderLine("MUG", 2, new BigDecimal("59.9"))));

        assertThat(OrderRequestFingerprint.of(equivalent)).isEqualTo(OrderRequestFingerprint.of(original));
    }

    @Test
    void changesWhenAnythingThatMattersChanges() {
        var original = OrderRequestFingerprint.of(new PlaceOrderCommand("WEB_STORE", "BRL", List.of(MUG)));

        assertThat(List.of(
                        new PlaceOrderCommand("MARKETPLACE", "BRL", List.of(MUG)),
                        new PlaceOrderCommand("WEB_STORE", "USD", List.of(MUG)),
                        new PlaceOrderCommand("WEB_STORE", "BRL", List.of(new OrderLine("MUG", 3, MUG.unitPrice()))),
                        new PlaceOrderCommand("WEB_STORE", "BRL", List.of(new OrderLine("MUG", 2, BigDecimal.ONE))),
                        new PlaceOrderCommand("WEB_STORE", "BRL", List.of(MUG, TEE))))
                .extracting(OrderRequestFingerprint::of)
                .doesNotContain(original)
                .doesNotHaveDuplicates();
    }

    @Test
    void isAHexEncodedSha256() {
        assertThat(OrderRequestFingerprint.of(new PlaceOrderCommand("WEB_STORE", "BRL", List.of(MUG))))
                .matches("[0-9a-f]{64}");
    }
}

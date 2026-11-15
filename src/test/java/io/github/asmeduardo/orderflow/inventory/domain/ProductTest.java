package io.github.asmeduardo.orderflow.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Test
    void newProductHasNothingReserved() {
        var product = new Product("MUG-BLUE-01", "Blue mug", 5, NOW);

        assertThat(product.getReservedQuantity()).isZero();
        assertThat(product.availableQuantity()).isEqualTo(5);
        assertThat(product.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsNegativeOnHandQuantity() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Product("MUG-BLUE-01", "Blue mug", -1, NOW));
    }
}

package io.github.asmeduardo.orderflow.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

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

    @Test
    void reservingMovesUnitsFromAvailableToReserved() {
        var product = new Product("MUG-BLUE-01", "Blue mug", 5, NOW);

        product.reserve(5, LATER);

        assertThat(product.getReservedQuantity()).isEqualTo(5);
        assertThat(product.availableQuantity()).isZero();
        assertThat(product.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void cannotReserveMoreThanIsAvailable() {
        var product = new Product("MUG-BLUE-01", "Blue mug", 5, NOW);
        product.reserve(4, NOW);

        assertThat(product.canReserve(2)).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> product.reserve(2, LATER));
        assertThat(product.getReservedQuantity()).isEqualTo(4);
    }

    @Test
    void quantitiesMustBePositive() {
        var product = new Product("MUG-BLUE-01", "Blue mug", 5, NOW);

        assertThatIllegalArgumentException().isThrownBy(() -> product.reserve(0, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> product.release(-1, LATER));
    }

    @Test
    void releasingReturnsUnitsButNeverMoreThanReserved() {
        var product = new Product("MUG-BLUE-01", "Blue mug", 5, NOW);
        product.reserve(3, NOW);

        product.release(2, LATER);

        assertThat(product.getReservedQuantity()).isEqualTo(1);
        assertThatIllegalStateException().isThrownBy(() -> product.release(2, LATER));
    }
}

package io.github.asmeduardo.orderflow.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.asmeduardo.orderflow.inventory.api.ReservationOutcome;
import io.github.asmeduardo.orderflow.inventory.api.StockReservations;
import io.github.asmeduardo.orderflow.orders.domain.IdempotencyKey;
import io.github.asmeduardo.orderflow.orders.domain.IdempotencyKeyRepository;
import io.github.asmeduardo.orderflow.orders.domain.Order;
import io.github.asmeduardo.orderflow.orders.domain.OrderLine;
import io.github.asmeduardo.orderflow.orders.domain.OrderRepository;
import io.github.asmeduardo.orderflow.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final IdempotencyKey.Id KEY =
            new IdempotencyKey.Id("web-store-integration", "c2f1e9a4-3b7d-4d9e-8f10-2a6b5c4d3e21");
    private static final PlaceOrderCommand COMMAND =
            new PlaceOrderCommand("WEB_STORE", "BRL", List.of(new OrderLine("MUG", 1, new BigDecimal("10.00"))));

    @Mock
    OrderRepository orders;

    @Mock
    IdempotencyKeyRepository keys;

    @Mock
    StockReservations stock;

    private OrderService service() {
        return new OrderService(
                orders, keys, stock, TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void replaysTheOriginalOrderWithoutReservingStockAgain() {
        var existing = confirmedOrder();
        when(keys.findById(KEY))
                .thenReturn(Optional.of(
                        new IdempotencyKey(KEY, OrderRequestFingerprint.of(COMMAND), existing.getId(), NOW)));
        when(orders.findWithItemsById(existing.getId())).thenReturn(Optional.of(existing));

        var result = service().place(KEY, COMMAND);

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().status()).isEqualTo(OrderStatus.CONFIRMED);
        verifyNoInteractions(stock);
        verify(orders, never()).save(any());
    }

    @Test
    void refusesAKeyAlreadyUsedForADifferentRequest() {
        when(keys.findById(KEY))
                .thenReturn(Optional.of(new IdempotencyKey(KEY, "f".repeat(64), UUID.randomUUID(), NOW)));

        assertThatThrownBy(() -> service().place(KEY, COMMAND)).isInstanceOf(IdempotencyKeyReusedException.class);
        verifyNoInteractions(stock);
    }

    @Test
    void returnsTheWinnerWhenAConcurrentRequestWithTheSameKeyCommitsFirst() {
        var winner = confirmedOrder();
        when(keys.findById(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(
                        Optional.of(new IdempotencyKey(KEY, OrderRequestFingerprint.of(COMMAND), winner.getId(), NOW)));
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(keys.saveAndFlush(any(IdempotencyKey.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates uk"));
        when(orders.findWithItemsById(winner.getId())).thenReturn(Optional.of(winner));

        var result = service().place(KEY, COMMAND);

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(winner.getId());
        verify(stock, never()).reserve(any(), anyList());
    }

    @Test
    void placesANewOrderWhenTheKeyIsUnknown() {
        when(keys.findById(KEY)).thenReturn(Optional.empty());
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stock.reserve(any(), anyList())).thenReturn(new ReservationOutcome.Reserved());

        var result = service().place(KEY, COMMAND);

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(keys).saveAndFlush(any(IdempotencyKey.class));
    }

    private static Order confirmedOrder() {
        var order = Order.place(
                "WEB_STORE",
                Currency.getInstance("BRL"),
                List.of(new OrderLine("MUG", 1, new BigDecimal("10.00"))),
                NOW);
        order.confirm(NOW);
        return order;
    }
}

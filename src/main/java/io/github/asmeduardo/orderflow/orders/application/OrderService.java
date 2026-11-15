package io.github.asmeduardo.orderflow.orders.application;

import io.github.asmeduardo.orderflow.inventory.api.ReservationOutcome;
import io.github.asmeduardo.orderflow.inventory.api.Shortage;
import io.github.asmeduardo.orderflow.inventory.api.StockLine;
import io.github.asmeduardo.orderflow.inventory.api.StockReservations;
import io.github.asmeduardo.orderflow.orders.domain.IdempotencyKey;
import io.github.asmeduardo.orderflow.orders.domain.IdempotencyKeyRepository;
import io.github.asmeduardo.orderflow.orders.domain.InvalidOrderException;
import io.github.asmeduardo.orderflow.orders.domain.Order;
import io.github.asmeduardo.orderflow.orders.domain.OrderRepository;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class OrderService {

    private static final int MAX_REASON_LENGTH = 500;

    private final OrderRepository orders;
    private final IdempotencyKeyRepository keys;
    private final StockReservations stock;
    private final TransactionOperations transactions;
    private final Clock clock;

    public OrderService(
            OrderRepository orders,
            IdempotencyKeyRepository keys,
            StockReservations stock,
            TransactionOperations transactions,
            Clock clock) {
        this.orders = orders;
        this.keys = keys;
        this.stock = stock;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Places an order at most once per idempotency key. A retry with the same payload gets the
     * original order back; the same key with a different payload is refused.
     */
    public PlacementResult place(String idempotencyKey, PlaceOrderCommand command) {
        var fingerprint = OrderRequestFingerprint.of(command);
        var previous = transactions.execute(status -> replay(idempotencyKey, fingerprint));
        if (previous.isPresent()) {
            return previous.get();
        }
        try {
            var order = transactions.execute(status -> placeNew(idempotencyKey, fingerprint, command));
            return new PlacementResult(order, false);
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same key committed first and this transaction rolled back,
            // stock reservation included. Answer with the order the winner created.
            return transactions
                    .execute(status -> replay(idempotencyKey, fingerprint))
                    .orElseThrow(() -> race);
        }
    }

    private Optional<PlacementResult> replay(String idempotencyKey, String fingerprint) {
        return keys.findById(idempotencyKey).map(previous -> {
            if (!previous.matches(fingerprint)) {
                throw new IdempotencyKeyReusedException();
            }
            var order = orders.findWithItemsById(previous.getOrderId())
                    .orElseThrow(() -> new IllegalStateException("Idempotency key points to a missing order"));
            return new PlacementResult(OrderView.of(order), true);
        });
    }

    /**
     * Records the order and reserves its stock in the same local transaction. The idempotency key
     * is flushed before stock is touched, so a concurrent retry blocks on its primary key instead of
     * reserving stock it would have to give back. The order is confirmed when every line is reserved
     * and rejected, with the shortages as reason, otherwise.
     */
    private OrderView placeNew(String idempotencyKey, String fingerprint, PlaceOrderCommand command) {
        var now = clock.instant();
        var order = orders.save(Order.place(command.channel(), currencyOf(command.currency()), command.lines(), now));
        keys.saveAndFlush(new IdempotencyKey(idempotencyKey, fingerprint, order.getId(), now));
        var lines = order.getItems().stream()
                .map(item -> new StockLine(item.getSku(), item.getQuantity()))
                .toList();
        switch (stock.reserve(order.getId(), lines)) {
            case ReservationOutcome.Reserved reserved -> order.confirm(clock.instant());
            case ReservationOutcome.Rejected rejected -> order.reject(describe(rejected.shortages()), clock.instant());
        }
        return OrderView.of(order);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID id) {
        return orders.findWithItemsById(id).map(OrderView::of).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private static Currency currencyOf(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderException("Unknown currency " + code);
        }
    }

    private static String describe(List<Shortage> shortages) {
        var reason = shortages.stream()
                .map(shortage -> switch (shortage.reason()) {
                    case UNKNOWN_PRODUCT -> "unknown product " + shortage.sku();
                    case INSUFFICIENT_STOCK ->
                        "insufficient stock for %s (requested %d, available %d)"
                                .formatted(shortage.sku(), shortage.requested(), shortage.available());
                })
                .collect(Collectors.joining("; "));
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH - 3) + "...";
    }
}

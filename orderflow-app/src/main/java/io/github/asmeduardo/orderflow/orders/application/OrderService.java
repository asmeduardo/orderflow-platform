package io.github.asmeduardo.orderflow.orders.application;

import io.github.asmeduardo.orderflow.orders.domain.IdempotencyKey;
import io.github.asmeduardo.orderflow.orders.domain.IdempotencyKeyRepository;
import io.github.asmeduardo.orderflow.orders.domain.InvalidOrderException;
import io.github.asmeduardo.orderflow.orders.domain.Order;
import io.github.asmeduardo.orderflow.orders.domain.OrderRepository;
import io.github.asmeduardo.orderflow.orders.domain.OrderStatus;
import java.time.Clock;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final IdempotencyKeyRepository keys;
    private final OrderEvents events;
    private final TransactionOperations transactions;
    private final Clock clock;

    public OrderService(
            OrderRepository orders,
            IdempotencyKeyRepository keys,
            OrderEvents events,
            TransactionOperations transactions,
            Clock clock) {
        this.orders = orders;
        this.keys = keys;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Places an order at most once per client and idempotency key. A retry with the same payload
     * gets the original order back; the same key with a different payload is refused.
     */
    public PlacementResult place(IdempotencyKey.Id idempotencyKey, PlaceOrderCommand command) {
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

    private Optional<PlacementResult> replay(IdempotencyKey.Id idempotencyKey, String fingerprint) {
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
     * Records the order, its idempotency key and the OrderPlaced event in one local transaction. The
     * key is flushed first, so a concurrent retry blocks on its primary key and then replays this
     * order. The order stays pending until the inventory reports the outcome of the reservation.
     */
    private OrderView placeNew(IdempotencyKey.Id idempotencyKey, String fingerprint, PlaceOrderCommand command) {
        var now = clock.instant();
        var order = orders.save(Order.place(command.channel(), currencyOf(command.currency()), command.lines(), now));
        keys.saveAndFlush(new IdempotencyKey(idempotencyKey, fingerprint, order.getId(), now));
        events.placed(order);
        return OrderView.of(order);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID id) {
        return orders.findWithItemsById(id).map(OrderView::of).orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Cancels the order and records the OrderCancelled event, which makes the inventory release the
     * stock. Cancelling an already cancelled order changes nothing. Two concurrent cancellations are
     * caught by the version column.
     */
    @Transactional
    public OrderView cancel(UUID id) {
        var order = orders.findWithItemsById(id).orElseThrow(() -> new OrderNotFoundException(id));
        if (order.cancel(clock.instant())) {
            events.cancelled(order);
        }
        return OrderView.of(order);
    }

    /** Newest orders first, optionally narrowed by status and channel. */
    @Transactional(readOnly = true)
    public Page<OrderSummary> list(OrderStatus status, String channel, int page, int size) {
        var newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        var filter = Specification.allOf(OrderRepository.withStatus(status), OrderRepository.fromChannel(channel));
        return orders.findAll(filter, PageRequest.of(page, size, newestFirst)).map(OrderSummary::of);
    }

    private static Currency currencyOf(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderException("Unknown currency " + code);
        }
    }
}

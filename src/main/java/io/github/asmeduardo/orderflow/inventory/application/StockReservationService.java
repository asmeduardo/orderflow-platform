package io.github.asmeduardo.orderflow.inventory.application;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import io.github.asmeduardo.orderflow.inventory.api.ReservationOutcome;
import io.github.asmeduardo.orderflow.inventory.api.Shortage;
import io.github.asmeduardo.orderflow.inventory.api.StockLine;
import io.github.asmeduardo.orderflow.inventory.api.StockReservations;
import io.github.asmeduardo.orderflow.inventory.domain.Product;
import io.github.asmeduardo.orderflow.inventory.domain.ProductRepository;
import io.github.asmeduardo.orderflow.inventory.domain.StockReservation;
import io.github.asmeduardo.orderflow.inventory.domain.StockReservationRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserves stock with row locks taken in a fixed order (see {@link ProductRepository}). The check
 * and the update happen while the rows are locked, so concurrent orders are serialized per product
 * and the outcome never depends on timing. The check constraint on {@code inventory.product} is the
 * last line of defence should any other code path ever skip the lock.
 */
@Service
class StockReservationService implements StockReservations {

    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final Clock clock;

    StockReservationService(ProductRepository products, StockReservationRepository reservations, Clock clock) {
        this.products = products;
        this.reservations = reservations;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReservationOutcome reserve(UUID orderId, List<StockLine> lines) {
        var requested = quantitiesBySku(lines);
        var locked = products.lockAllBySku(requested.keySet()).stream().collect(toMap(Product::getSku, identity()));
        // Checked only after locking: a concurrent call for the same order has committed by now.
        if (reservations.existsByOrderId(orderId)) {
            return new ReservationOutcome.Reserved();
        }
        var shortages = shortages(requested, locked);
        if (!shortages.isEmpty()) {
            return new ReservationOutcome.Rejected(shortages);
        }
        var now = clock.instant();
        requested.forEach((sku, quantity) -> {
            var product = locked.get(sku);
            product.reserve(quantity, now);
            reservations.save(new StockReservation(orderId, product, quantity, now));
        });
        return new ReservationOutcome.Reserved();
    }

    @Override
    @Transactional
    public void release(UUID orderId) {
        var active = reservations.findByOrderIdAndStatus(orderId, StockReservation.Status.RESERVED);
        if (active.isEmpty()) {
            return;
        }
        var productIds = active.stream().map(reservation -> reservation.getProduct().getId()).toList();
        var locked = products.lockAllById(productIds).stream().collect(toMap(Product::getId, identity()));
        var now = clock.instant();
        for (var reservation : active) {
            locked.get(reservation.getProduct().getId()).release(reservation.getQuantity(), now);
            reservation.markReleased(now);
        }
    }

    private static List<Shortage> shortages(SortedMap<String, Integer> requested, Map<String, Product> locked) {
        var shortages = new ArrayList<Shortage>();
        requested.forEach((sku, quantity) -> {
            var product = locked.get(sku);
            if (product == null) {
                shortages.add(new Shortage(sku, Shortage.Reason.UNKNOWN_PRODUCT, quantity, 0));
            } else if (!product.canReserve(quantity)) {
                shortages.add(new Shortage(
                        sku, Shortage.Reason.INSUFFICIENT_STOCK, quantity, product.availableQuantity()));
            }
        });
        return shortages;
    }

    private static SortedMap<String, Integer> quantitiesBySku(List<StockLine> lines) {
        var bySku = new TreeMap<String, Integer>();
        lines.forEach(line -> bySku.merge(line.sku(), line.quantity(), Integer::sum));
        return bySku;
    }
}

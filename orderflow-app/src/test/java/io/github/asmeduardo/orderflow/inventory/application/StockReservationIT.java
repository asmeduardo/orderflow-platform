package io.github.asmeduardo.orderflow.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.orderflow.inventory.api.ReservationOutcome;
import io.github.asmeduardo.orderflow.inventory.api.ReservationOutcome.Rejected;
import io.github.asmeduardo.orderflow.inventory.api.ReservationOutcome.Reserved;
import io.github.asmeduardo.orderflow.inventory.api.Shortage;
import io.github.asmeduardo.orderflow.inventory.api.StockLine;
import io.github.asmeduardo.orderflow.inventory.api.StockReservations;
import io.github.asmeduardo.orderflow.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class StockReservationIT {

    @Autowired
    StockReservations reservations;

    @Autowired
    ProductService products;

    @Test
    void reservesEveryLineWhenStockIsAvailable() {
        products.register("RSV-A1", "Item A1", 5);
        products.register("RSV-A2", "Item A2", 2);

        var outcome = reservations.reserve(
                UUID.randomUUID(), List.of(new StockLine("RSV-A1", 3), new StockLine("RSV-A2", 2)));

        assertThat(outcome).isInstanceOf(Reserved.class);
        assertThat(products.get("RSV-A1").availableQuantity()).isEqualTo(2);
        assertThat(products.get("RSV-A2").reservedQuantity()).isEqualTo(2);
    }

    @Test
    void reservesNothingWhenAnyLineIsShort() {
        products.register("RSV-B1", "Item B1", 5);
        products.register("RSV-B2", "Item B2", 1);

        var outcome = reservations.reserve(
                UUID.randomUUID(),
                List.of(new StockLine("RSV-B1", 2), new StockLine("RSV-B2", 3), new StockLine("RSV-NOPE", 1)));

        assertThat(outcome)
                .isEqualTo(new Rejected(List.of(
                        new Shortage("RSV-B2", Shortage.Reason.INSUFFICIENT_STOCK, 3, 1),
                        new Shortage("RSV-NOPE", Shortage.Reason.UNKNOWN_PRODUCT, 1, 0))));
        assertThat(products.get("RSV-B1").reservedQuantity()).isZero();
        assertThat(products.get("RSV-B2").reservedQuantity()).isZero();
    }

    @Test
    void reservingTheSameOrderTwiceDoesNotReserveTwice() {
        products.register("RSV-C1", "Item C1", 5);
        var orderId = UUID.randomUUID();

        reservations.reserve(orderId, List.of(new StockLine("RSV-C1", 2)));
        var replay = reservations.reserve(orderId, List.of(new StockLine("RSV-C1", 2)));

        assertThat(replay).isInstanceOf(Reserved.class);
        assertThat(products.get("RSV-C1").reservedQuantity()).isEqualTo(2);
    }

    @Test
    void releaseReturnsReservedStockOnlyOnce() {
        products.register("RSV-D1", "Item D1", 4);
        var orderId = UUID.randomUUID();
        reservations.reserve(orderId, List.of(new StockLine("RSV-D1", 3)));

        reservations.release(orderId);
        reservations.release(orderId);

        assertThat(products.get("RSV-D1").reservedQuantity()).isZero();
        assertThat(products.get("RSV-D1").availableQuantity()).isEqualTo(4);
    }

    @Test
    void concurrentOrdersNeverReserveMoreThanIsOnHand() throws Exception {
        products.register("RSV-HOT", "Last units on sale", 10);
        int buyers = 40;

        var outcomes = runConcurrently(
                buyers, () -> reservations.reserve(UUID.randomUUID(), List.of(new StockLine("RSV-HOT", 1))));

        assertThat(outcomes).filteredOn(Reserved.class::isInstance).hasSize(10);
        assertThat(outcomes).filteredOn(Rejected.class::isInstance).hasSize(buyers - 10);
        assertThat(products.get("RSV-HOT").reservedQuantity()).isEqualTo(10);
        assertThat(products.get("RSV-HOT").availableQuantity()).isZero();
    }

    @Test
    void ordersLockingTheSameProductsInOppositeOrderDoNotDeadlock() throws Exception {
        products.register("RSV-X", "Item X", 100);
        products.register("RSV-Y", "Item Y", 100);
        var forward = List.of(new StockLine("RSV-X", 1), new StockLine("RSV-Y", 1));
        var backward = List.of(new StockLine("RSV-Y", 1), new StockLine("RSV-X", 1));
        int orders = 30;
        var counter = new java.util.concurrent.atomic.AtomicInteger();

        var outcomes = runConcurrently(
                orders,
                () -> reservations.reserve(UUID.randomUUID(), counter.getAndIncrement() % 2 == 0 ? forward : backward));

        assertThat(outcomes).allMatch(Reserved.class::isInstance);
        assertThat(products.get("RSV-X").reservedQuantity()).isEqualTo(orders);
        assertThat(products.get("RSV-Y").reservedQuantity()).isEqualTo(orders);
    }

    private static List<ReservationOutcome> runConcurrently(int tasks, Callable<ReservationOutcome> task)
            throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tasks)) {
            var futures = new ArrayList<Future<ReservationOutcome>>();
            for (int i = 0; i < tasks; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            var outcomes = new ArrayList<ReservationOutcome>();
            for (var future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        }
    }
}

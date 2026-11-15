package io.github.asmeduardo.orderflow.inventory.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    boolean existsByOrderId(UUID orderId);

    /** Locks the matching reservations so that two releases of one order cannot both apply. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StockReservation> findByOrderIdAndStatus(UUID orderId, StockReservation.Status status);
}

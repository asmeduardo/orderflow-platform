package io.github.asmeduardo.orderflow.orders.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    static Specification<Order> withStatus(OrderStatus status) {
        return (order, query, builder) -> status == null ? null : builder.equal(order.get("status"), status);
    }

    static Specification<Order> fromChannel(String channel) {
        return (order, query, builder) -> channel == null ? null : builder.equal(order.get("channel"), channel);
    }
}

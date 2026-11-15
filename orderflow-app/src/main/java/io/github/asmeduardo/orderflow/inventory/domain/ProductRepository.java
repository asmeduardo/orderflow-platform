package io.github.asmeduardo.orderflow.inventory.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Product> findBySkuStartingWith(String skuPrefix, Pageable pageable);

    /**
     * Locks the rows until the transaction ends. Rows are always locked in SKU order so that two
     * orders touching the same products cannot deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.sku in :skus order by p.sku")
    List<Product> lockAllBySku(Collection<String> skus);

    /** Same locking order as {@link #lockAllBySku(Collection)}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.sku")
    List<Product> lockAllById(Collection<Long> ids);
}

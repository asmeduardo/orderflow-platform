package io.github.asmeduardo.orderflow.inventory.application;

import io.github.asmeduardo.orderflow.inventory.domain.Product;
import io.github.asmeduardo.orderflow.inventory.domain.ProductRepository;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final Clock clock;

    public ProductService(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    /**
     * Registers a new product. A concurrent registration of the same SKU that slips past the
     * existence check is still rejected by the unique constraint on {@code inventory.product.sku}.
     */
    @Transactional
    public ProductView register(String sku, String name, int onHandQuantity) {
        if (products.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }
        var product = products.saveAndFlush(new Product(sku, name, onHandQuantity, clock.instant()));
        return ProductView.of(product);
    }

    @Transactional(readOnly = true)
    public ProductView get(String sku) {
        return products.findBySku(sku).map(ProductView::of).orElseThrow(() -> new ProductNotFoundException(sku));
    }

    @Transactional(readOnly = true)
    public Page<ProductView> list(String skuPrefix, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("sku"));
        return products.findBySkuStartingWith(skuPrefix == null ? "" : skuPrefix, pageable)
                .map(ProductView::of);
    }
}

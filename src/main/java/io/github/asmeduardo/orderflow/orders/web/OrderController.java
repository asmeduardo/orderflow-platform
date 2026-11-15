package io.github.asmeduardo.orderflow.orders.web;

import io.github.asmeduardo.orderflow.orders.application.OrderService;
import io.github.asmeduardo.orderflow.orders.application.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * Places an order. Retrying with the same {@code Idempotency-Key} and payload returns the
     * original response, marked with {@code Idempotent-Replayed: true}.
     */
    @PostMapping
    public ResponseEntity<OrderView> place(
            @RequestHeader(IDEMPOTENCY_KEY)
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}", message = "must be 8-100 characters: letters, digits or . _ : -")
                    String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        var result = orders.place(idempotencyKey, request.toCommand());
        var response = ResponseEntity.created(URI.create("/api/v1/orders/" + result.order().id()));
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(result.order());
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable UUID id) {
        return orders.get(id);
    }
}

package io.github.asmeduardo.orderflow.orders.web;

import io.github.asmeduardo.orderflow.orders.application.OrderService;
import io.github.asmeduardo.orderflow.orders.application.OrderView;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<OrderView> place(@Valid @RequestBody PlaceOrderRequest request) {
        var order = orders.place(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order);
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable UUID id) {
        return orders.get(id);
    }
}

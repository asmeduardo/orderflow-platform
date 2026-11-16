package io.github.asmeduardo.orderflow.orders.web;

import io.github.asmeduardo.orderflow.orders.application.OrderService;
import io.github.asmeduardo.orderflow.orders.application.OrderSummary;
import io.github.asmeduardo.orderflow.orders.application.OrderView;
import io.github.asmeduardo.orderflow.orders.domain.IdempotencyKey;
import io.github.asmeduardo.orderflow.orders.domain.OrderStatus;
import io.github.asmeduardo.orderflow.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order intake, query and cancellation")
public class OrderController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";
    private static final String PROBLEM_JSON = "application/problem+json";

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * Places an order. Retrying with the same {@code Idempotency-Key} and payload returns the
     * original response, marked with {@code Idempotent-Replayed: true}.
     */
    @PostMapping
    @Operation(
            summary = "Place an order",
            description = "Records the order and tries to reserve its stock. Retrying with the same "
                    + "Idempotency-Key and payload returns the original order with Idempotent-Replayed: true.")
    @ApiResponse(responseCode = "201", description = "Order recorded; see status for the reservation outcome")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request or order",
            content = @Content(mediaType = PROBLEM_JSON))
    @ApiResponse(
            responseCode = "422",
            description = "Idempotency key already used for a different payload",
            content = @Content(mediaType = PROBLEM_JSON))
    public ResponseEntity<OrderView> place(
            @Parameter(
                            description =
                                    "Client-generated key, unique per order attempt for the calling client (a UUID is a good choice)")
                    @RequestHeader(IDEMPOTENCY_KEY)
                    @Pattern(
                            regexp = "[A-Za-z0-9._:-]{8,100}",
                            message = "must be 8-100 characters: letters, digits or . _ : -")
                    String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request,
            @AuthenticationPrincipal Jwt client) {
        var key = new IdempotencyKey.Id(client.getSubject(), idempotencyKey);
        var result = orders.place(key, request.toCommand());
        var response = ResponseEntity.created(
                URI.create("/api/v1/orders/" + result.order().id()));
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(result.order());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order with its items")
    @ApiResponse(responseCode = "404", description = "Unknown order", content = @Content(mediaType = PROBLEM_JSON))
    public OrderView get(@PathVariable UUID id) {
        return orders.get(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Cancel an order and release its stock",
            description = "Cancelling twice has no further effect.")
    @ApiResponse(
            responseCode = "409",
            description = "Order was rejected or changed concurrently",
            content = @Content(mediaType = PROBLEM_JSON))
    public OrderView cancel(@PathVariable UUID id) {
        return orders.cancel(id);
    }

    @GetMapping
    @Operation(summary = "List orders, newest first")
    public PageResponse<OrderSummary> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(orders.list(status, channel, page, size));
    }
}

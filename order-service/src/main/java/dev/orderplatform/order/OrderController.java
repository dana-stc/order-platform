package dev.orderplatform.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;
    private final OrderRepository orders;

    public OrderController(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    /** 202 Accepted: the saga continues asynchronously, poll GET /orders/{id} for the outcome. */
    @PostMapping
    public ResponseEntity<OrderDtos.OrderResponse> create(
            @Valid @RequestBody OrderDtos.CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        OrderService.CreateResult result = service.create(request, idempotencyKey);
        return ResponseEntity
                .status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .location(URI.create("/orders/" + result.order().getId()))
                .body(OrderDtos.OrderResponse.from(result.order()));
    }

    @GetMapping("/{id}")
    public OrderDtos.OrderResponse get(@PathVariable UUID id) {
        return orders.findById(id)
                .map(OrderDtos.OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + id + " not found"));
    }
}

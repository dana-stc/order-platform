package dev.orderplatform.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(
            @NotBlank String customerId,
            @NotEmpty List<@Valid Line> items) {
    }

    public record Line(
            @NotBlank String sku,
            @Min(1) int quantity,
            @DecimalMin("0.01") BigDecimal unitPrice) {
    }

    public record OrderResponse(
            UUID id,
            String customerId,
            OrderStatus status,
            BigDecimal totalAmount,
            String failureReason,
            Instant createdAt,
            Instant updatedAt,
            List<Line> items) {

        public static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getCustomerId(), o.getStatus(), o.getTotalAmount(),
                    o.getFailureReason(), o.getCreatedAt(), o.getUpdatedAt(),
                    o.getLines().stream().map(l -> new Line(l.getSku(), l.getQuantity(), l.getUnitPrice())).toList());
        }
    }
}

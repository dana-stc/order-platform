package dev.orderplatform.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {
    }

    public Notification(UUID orderId, String customerId, String type, String body) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.type = type;
        this.body = body;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getType() {
        return type;
    }

    public String getBody() {
        return body;
    }
}

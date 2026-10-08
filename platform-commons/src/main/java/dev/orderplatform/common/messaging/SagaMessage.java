package dev.orderplatform.common.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Single envelope for every command, reply and event of the order saga.
 * <p>
 * {@code messageId} is the deduplication key used by consumers (idempotent consumer);
 * {@code orderId} is also the Kafka key, which guarantees per-order ordering.
 */
public record SagaMessage(
        UUID messageId,
        UUID orderId,
        MessageType type,
        String customerId,
        BigDecimal amount,
        List<Item> items,
        String reason,
        Instant occurredAt) {

    public record Item(String sku, int quantity) {
    }

    public static SagaMessage of(MessageType type, UUID orderId) {
        return new SagaMessage(UUID.randomUUID(), orderId, type, null, null, List.of(), null, Instant.now());
    }

    public SagaMessage withCustomerId(String customerId) {
        return new SagaMessage(messageId, orderId, type, customerId, amount, items, reason, occurredAt);
    }

    public SagaMessage withAmount(BigDecimal amount) {
        return new SagaMessage(messageId, orderId, type, customerId, amount, items, reason, occurredAt);
    }

    public SagaMessage withItems(List<Item> items) {
        return new SagaMessage(messageId, orderId, type, customerId, amount, items, reason, occurredAt);
    }

    public SagaMessage withReason(String reason) {
        return new SagaMessage(messageId, orderId, type, customerId, amount, items, reason, occurredAt);
    }
}

package dev.orderplatform.order;

import dev.orderplatform.common.messaging.IdempotencyGuard;
import dev.orderplatform.common.messaging.MessageType;
import dev.orderplatform.common.messaging.SagaMessage;
import dev.orderplatform.common.messaging.Topics;
import dev.orderplatform.common.outbox.OutboxService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Orchestration-based saga for an order:
 * <pre>
 *  PENDING --STOCK_RESERVED--> STOCK_RESERVED --PAYMENT_COMPLETED--> CONFIRMED
 *     |                              |
 *     | STOCK_REJECTED               | PAYMENT_FAILED
 *     v                              v
 *  REJECTED  <--STOCK_RELEASED--  COMPENSATING
 * </pre>
 * Every reply is handled in ONE transaction: idempotency record + state change + next command (outbox).
 * Replies that do not match the current state (duplicates, late arrivals) are ignored.
 */
@Service
public class SagaOrchestrator {

    static final String CONSUMER = "order-saga";
    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final OrderRepository orders;
    private final OutboxService outbox;
    private final IdempotencyGuard idempotency;
    private final MeterRegistry meters;

    public SagaOrchestrator(OrderRepository orders, OutboxService outbox, IdempotencyGuard idempotency,
                            MeterRegistry meters) {
        this.orders = orders;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.meters = meters;
    }

    @Transactional
    public void handle(SagaMessage reply) {
        if (!idempotency.firstTime(reply.messageId(), CONSUMER)) {
            log.info("Duplicate {} {} ignored", reply.type(), reply.messageId());
            return;
        }
        Order order = orders.findForUpdate(reply.orderId()).orElse(null);
        if (order == null) {
            log.warn("{} for unknown order {} ignored", reply.type(), reply.orderId());
            return;
        }

        switch (reply.type()) {
            case STOCK_RESERVED -> onStockReserved(order);
            case STOCK_REJECTED -> onStockRejected(order, reply.reason());
            case PAYMENT_COMPLETED -> onPaymentCompleted(order);
            case PAYMENT_FAILED -> onPaymentFailed(order, reply.reason());
            case STOCK_RELEASED -> onStockReleased(order);
            default -> log.warn("Unexpected message type {} on {}", reply.type(), Topics.SAGA_REPLIES);
        }
    }

    private void onStockReserved(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            ignored(order, MessageType.STOCK_RESERVED);
            return;
        }
        order.transitionTo(OrderStatus.STOCK_RESERVED, null);
        outbox.enqueue(Topics.PAYMENT_COMMANDS, SagaMessage.of(MessageType.PROCESS_PAYMENT, order.getId())
                .withCustomerId(order.getCustomerId())
                .withAmount(order.getTotalAmount()));
    }

    private void onStockRejected(Order order, String reason) {
        if (order.getStatus() != OrderStatus.PENDING) {
            ignored(order, MessageType.STOCK_REJECTED);
            return;
        }
        reject(order, reason);
    }

    private void onPaymentCompleted(Order order) {
        if (order.getStatus() != OrderStatus.STOCK_RESERVED) {
            ignored(order, MessageType.PAYMENT_COMPLETED);
            return;
        }
        order.transitionTo(OrderStatus.CONFIRMED, null);
        outbox.enqueue(Topics.ORDER_EVENTS, SagaMessage.of(MessageType.ORDER_CONFIRMED, order.getId())
                .withCustomerId(order.getCustomerId())
                .withAmount(order.getTotalAmount()));
        finished(order, "confirmed");
    }

    private void onPaymentFailed(Order order, String reason) {
        if (order.getStatus() != OrderStatus.STOCK_RESERVED) {
            ignored(order, MessageType.PAYMENT_FAILED);
            return;
        }
        // Compensation: undo the only step that already succeeded.
        order.transitionTo(OrderStatus.COMPENSATING, reason);
        outbox.enqueue(Topics.INVENTORY_COMMANDS, SagaMessage.of(MessageType.RELEASE_STOCK, order.getId()));
    }

    private void onStockReleased(Order order) {
        if (order.getStatus() != OrderStatus.COMPENSATING) {
            ignored(order, MessageType.STOCK_RELEASED);
            return;
        }
        reject(order, null);
    }

    private void reject(Order order, String reason) {
        order.transitionTo(OrderStatus.REJECTED, reason);
        outbox.enqueue(Topics.ORDER_EVENTS, SagaMessage.of(MessageType.ORDER_REJECTED, order.getId())
                .withCustomerId(order.getCustomerId())
                .withReason(order.getFailureReason()));
        finished(order, "rejected");
    }

    private void finished(Order order, String outcome) {
        meters.counter("orders.finished", "outcome", outcome).increment();
        meters.timer("orders.saga.duration", "outcome", outcome)
                .record(Duration.between(order.getCreatedAt(), Instant.now()));
        log.info("Order {} {} ({})", order.getId(), outcome, order.getFailureReason());
    }

    private void ignored(Order order, MessageType type) {
        log.info("{} ignored: order {} is {}", type, order.getId(), order.getStatus());
    }
}

package dev.orderplatform.order;

import dev.orderplatform.common.messaging.IdempotencyGuard;
import dev.orderplatform.common.messaging.MessageType;
import dev.orderplatform.common.messaging.SagaMessage;
import dev.orderplatform.common.messaging.Topics;
import dev.orderplatform.common.outbox.OutboxService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SagaOrchestratorTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final IdempotencyGuard idempotency = mock(IdempotencyGuard.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final SagaOrchestrator orchestrator = new SagaOrchestrator(orders, outbox, idempotency, meters);

    private final UUID orderId = UUID.randomUUID();
    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order("customer-1", List.of(new OrderLine("SKU-PHONE", 2, new BigDecimal("100.00"))), null);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(idempotency.firstTime(any(), anyString())).thenReturn(true);
        when(orders.findForUpdate(orderId)).thenReturn(Optional.of(order));
    }

    @Test
    void stockReserved_movesToStockReserved_andRequestsPayment() {
        orchestrator.handle(reply(MessageType.STOCK_RESERVED, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
        SagaMessage command = enqueued(Topics.PAYMENT_COMMANDS);
        assertThat(command.type()).isEqualTo(MessageType.PROCESS_PAYMENT);
        assertThat(command.amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void stockRejected_rejectsOrder_andPublishesOrderRejected() {
        orchestrator.handle(reply(MessageType.STOCK_REJECTED, "Insufficient stock for SKU-PHONE"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getFailureReason()).isEqualTo("Insufficient stock for SKU-PHONE");
        SagaMessage event = enqueued(Topics.ORDER_EVENTS);
        assertThat(event.type()).isEqualTo(MessageType.ORDER_REJECTED);
        assertThat(event.reason()).contains("Insufficient stock");
    }

    @Test
    void paymentCompleted_confirmsOrder() {
        order.transitionTo(OrderStatus.STOCK_RESERVED, null);

        orchestrator.handle(reply(MessageType.PAYMENT_COMPLETED, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(enqueued(Topics.ORDER_EVENTS).type()).isEqualTo(MessageType.ORDER_CONFIRMED);
        assertThat(meters.get("orders.finished").tag("outcome", "confirmed").counter().count()).isEqualTo(1.0);
    }

    @Test
    void paymentFailed_startsCompensation_byReleasingStock() {
        order.transitionTo(OrderStatus.STOCK_RESERVED, null);

        orchestrator.handle(reply(MessageType.PAYMENT_FAILED, "declined"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPENSATING);
        assertThat(enqueued(Topics.INVENTORY_COMMANDS).type()).isEqualTo(MessageType.RELEASE_STOCK);
    }

    @Test
    void stockReleased_afterCompensation_rejectsOrderKeepingTheOriginalReason() {
        order.transitionTo(OrderStatus.COMPENSATING, "declined");

        orchestrator.handle(reply(MessageType.STOCK_RELEASED, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        SagaMessage event = enqueued(Topics.ORDER_EVENTS);
        assertThat(event.type()).isEqualTo(MessageType.ORDER_REJECTED);
        assertThat(event.reason()).isEqualTo("declined");
    }

    @Test
    void duplicateMessage_isIgnored() {
        SagaMessage duplicate = reply(MessageType.STOCK_RESERVED, null);
        when(idempotency.firstTime(duplicate.messageId(), SagaOrchestrator.CONSUMER)).thenReturn(false);

        orchestrator.handle(duplicate);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(outbox);
    }

    @Test
    void outOfOrderReply_isIgnored() {
        // PAYMENT_COMPLETED can only be valid after STOCK_RESERVED.
        orchestrator.handle(reply(MessageType.PAYMENT_COMPLETED, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(outbox, never()).enqueue(anyString(), any());
    }

    @Test
    void replyForUnknownOrder_isIgnored() {
        when(orders.findForUpdate(orderId)).thenReturn(Optional.empty());

        orchestrator.handle(reply(MessageType.STOCK_RESERVED, null));

        verifyNoInteractions(outbox);
    }

    private SagaMessage reply(MessageType type, String reason) {
        return SagaMessage.of(type, orderId).withReason(reason);
    }

    private SagaMessage enqueued(String topic) {
        ArgumentCaptor<SagaMessage> captor = ArgumentCaptor.forClass(SagaMessage.class);
        verify(outbox).enqueue(org.mockito.ArgumentMatchers.eq(topic), captor.capture());
        return captor.getValue();
    }
}

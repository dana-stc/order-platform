package dev.orderplatform.payment;

import dev.orderplatform.common.messaging.IdempotencyGuard;
import dev.orderplatform.common.messaging.MessageType;
import dev.orderplatform.common.messaging.SagaMessage;
import dev.orderplatform.common.messaging.Topics;
import dev.orderplatform.common.outbox.OutboxService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentHandlerTest {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final IdempotencyGuard idempotency = mock(IdempotencyGuard.class);
    private final PaymentHandler handler =
            new PaymentHandler(payments, outbox, idempotency, new SimpleMeterRegistry(), new BigDecimal("1000"));

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(idempotency.firstTime(any(), anyString())).thenReturn(true);
    }

    @Test
    void amountWithinLimit_completesPayment() {
        handler.handle(command(new BigDecimal("999.99")));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(Payment.Status.COMPLETED);
        assertThat(reply().type()).isEqualTo(MessageType.PAYMENT_COMPLETED);
    }

    @Test
    void amountAboveLimit_failsPayment_withReason() {
        handler.handle(command(new BigDecimal("1000.01")));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(Payment.Status.FAILED);
        SagaMessage reply = reply();
        assertThat(reply.type()).isEqualTo(MessageType.PAYMENT_FAILED);
        assertThat(reply.reason()).contains("exceeds limit");
    }

    @Test
    void duplicateCommand_isIgnored() {
        SagaMessage command = command(new BigDecimal("10"));
        when(idempotency.firstTime(command.messageId(), PaymentHandler.CONSUMER)).thenReturn(false);

        handler.handle(command);

        verify(payments, never()).save(any());
        verify(outbox, never()).enqueue(anyString(), any());
    }

    private SagaMessage command(BigDecimal amount) {
        return SagaMessage.of(MessageType.PROCESS_PAYMENT, orderId).withAmount(amount);
    }

    private SagaMessage reply() {
        ArgumentCaptor<SagaMessage> captor = ArgumentCaptor.forClass(SagaMessage.class);
        verify(outbox).enqueue(eq(Topics.SAGA_REPLIES), captor.capture());
        return captor.getValue();
    }
}

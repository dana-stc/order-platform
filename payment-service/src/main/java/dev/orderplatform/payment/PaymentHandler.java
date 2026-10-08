package dev.orderplatform.payment;

import dev.orderplatform.common.messaging.IdempotencyGuard;
import dev.orderplatform.common.messaging.MessageType;
import dev.orderplatform.common.messaging.SagaMessage;
import dev.orderplatform.common.messaging.Topics;
import dev.orderplatform.common.outbox.OutboxService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Simulated payment provider. A payment is declined when the amount exceeds {@code payment.max-amount},
 * which is how the demo triggers the saga compensation path.
 */
@Service
public class PaymentHandler {

    static final String CONSUMER = "payment-service";
    private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);

    private final PaymentRepository payments;
    private final OutboxService outbox;
    private final IdempotencyGuard idempotency;
    private final MeterRegistry meters;
    private final BigDecimal maxAmount;

    public PaymentHandler(PaymentRepository payments, OutboxService outbox, IdempotencyGuard idempotency,
                          MeterRegistry meters, @Value("${payment.max-amount:1000}") BigDecimal maxAmount) {
        this.payments = payments;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.meters = meters;
        this.maxAmount = maxAmount;
    }

    @Transactional
    public void handle(SagaMessage command) {
        if (!idempotency.firstTime(command.messageId(), CONSUMER)) {
            log.info("Duplicate {} {} ignored", command.type(), command.messageId());
            return;
        }
        if (command.type() != MessageType.PROCESS_PAYMENT) {
            log.warn("Unexpected command {}", command.type());
            return;
        }

        boolean declined = command.amount().compareTo(maxAmount) > 0;
        if (declined) {
            String reason = "Payment declined: amount " + command.amount() + " exceeds limit " + maxAmount;
            payments.save(new Payment(command.orderId(), command.amount(), Payment.Status.FAILED, reason));
            outbox.enqueue(Topics.SAGA_REPLIES,
                    SagaMessage.of(MessageType.PAYMENT_FAILED, command.orderId()).withReason(reason));
        } else {
            payments.save(new Payment(command.orderId(), command.amount(), Payment.Status.COMPLETED, null));
            outbox.enqueue(Topics.SAGA_REPLIES, SagaMessage.of(MessageType.PAYMENT_COMPLETED, command.orderId()));
        }
        meters.counter("payments.processed", "result", declined ? "failed" : "completed").increment();
    }
}

package dev.orderplatform.common.outbox;

import dev.orderplatform.common.messaging.MessageCodec;
import dev.orderplatform.common.messaging.SagaMessage;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox, write side. The message is stored in the SAME database transaction as the business
 * change; {@link OutboxPublisher} ships it to Kafka afterwards. This removes the dual-write problem
 * (DB commit succeeded but the Kafka send failed, or the other way round).
 */
@Component
public class OutboxService {

    private final OutboxRepository repository;
    private final MessageCodec codec;
    private final ObjectProvider<Tracer> tracer;

    public OutboxService(OutboxRepository repository, MessageCodec codec, ObjectProvider<Tracer> tracer) {
        this.repository = repository;
        this.codec = codec;
        this.tracer = tracer;
    }

    /** MANDATORY: calling this outside a business transaction is a programming error, fail fast. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String topic, SagaMessage message) {
        String traceId = null;
        String spanId = null;
        Tracer t = tracer.getIfAvailable();
        if (t != null) {
            Span current = t.currentSpan();
            if (current != null) {
                traceId = current.context().traceId();
                spanId = current.context().spanId();
            }
        }
        repository.save(new OutboxEvent(
                message.orderId().toString(), topic, message.type().name(), codec.toJson(message), traceId, spanId));
    }
}

package dev.orderplatform.common.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Transactional outbox, read side: a poller that publishes pending rows to Kafka.
 * <p>
 * Delivery is AT-LEAST-ONCE: if the app crashes after the Kafka ack but before the commit that marks the row as
 * published, the row is sent again. Consumers therefore deduplicate on {@code messageId} (see IdempotencyGuard).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectProvider<Tracer> tracer;
    private final Counter published;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository repository,
                           KafkaTemplate<String, String> kafka,
                           ObjectProvider<Tracer> tracer,
                           MeterRegistry meters,
                           @Value("${outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.kafka = kafka;
        this.tracer = tracer;
        this.batchSize = batchSize;
        this.published = Counter.builder("outbox.published").description("Outbox rows sent to Kafka").register(meters);
        Gauge.builder("outbox.pending", repository, OutboxRepository::countByPublishedAtIsNull)
                .description("Outbox rows waiting to be published (alert if it keeps growing)")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockNextBatch(batchSize);
        for (OutboxEvent event : batch) {
            send(event);
            event.markPublished();
            published.increment();
        }
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup-interval-ms:600000}")
    @Transactional
    public void cleanup() {
        int deleted = repository.deletePublishedBefore(Instant.now().minus(Duration.ofHours(1)));
        if (deleted > 0) {
            log.debug("Removed {} published outbox rows", deleted);
        }
    }

    private void send(OutboxEvent event) {
        Tracer t = tracer.getIfAvailable();
        Span span = null;
        if (t != null && event.getTraceId() != null) {
            // Continue the trace of the request/message that produced this row.
            TraceContext parent = t.traceContextBuilder()
                    .traceId(event.getTraceId())
                    .spanId(event.getSpanId())
                    .sampled(true)
                    .build();
            span = t.spanBuilder().setParent(parent).name("outbox publish " + event.getMessageType()).start();
        }
        try (Tracer.SpanInScope ignored = span == null ? null : t.withSpan(span)) {
            // Block on the ack: only mark the row published once the broker has it.
            kafka.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get(10, TimeUnit.SECONDS);
            log.debug("Published {} to {}", event.getMessageType(), event.getTopic());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox row " + event.getId(), e);
        } catch (Exception e) {
            // Roll back the batch; the rows stay pending and are retried on the next poll.
            throw new IllegalStateException("Failed to publish outbox row " + event.getId(), e);
        } finally {
            if (span != null) {
                span.end();
            }
        }
    }
}

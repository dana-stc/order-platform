package dev.orderplatform.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    /** Trace context captured when the business transaction ran, so the async publish stays in the same trace. */
    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "span_id")
    private String spanId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateId, String topic, String messageType, String payload,
                       String traceId, String spanId) {
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.messageType = messageType;
        this.payload = payload;
        this.traceId = traceId;
        this.spanId = spanId;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getPayload() {
        return payload;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}

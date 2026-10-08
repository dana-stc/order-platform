package dev.orderplatform.notification;

import dev.orderplatform.common.messaging.IdempotencyGuard;
import dev.orderplatform.common.messaging.MessageCodec;
import dev.orderplatform.common.messaging.SagaMessage;
import dev.orderplatform.common.messaging.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Pure event consumer: it is not part of the saga, it only reacts to the final outcome of an order. */
@Component
public class OrderEventListener {

    static final String CONSUMER = "notification-service";
    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final MessageCodec codec;
    private final IdempotencyGuard idempotency;
    private final NotificationRepository notifications;
    private final MeterRegistry meters;

    public OrderEventListener(MessageCodec codec, IdempotencyGuard idempotency, NotificationRepository notifications,
                              MeterRegistry meters) {
        this.codec = codec;
        this.idempotency = idempotency;
        this.notifications = notifications;
        this.meters = meters;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "notification-service")
    @Transactional
    public void onOrderEvent(String payload) {
        SagaMessage event = codec.fromJson(payload);
        if (!idempotency.firstTime(event.messageId(), CONSUMER)) {
            log.info("Duplicate {} {} ignored", event.type(), event.messageId());
            return;
        }
        String body = switch (event.type()) {
            case ORDER_CONFIRMED -> "Your order " + event.orderId() + " is confirmed (total " + event.amount() + ").";
            case ORDER_REJECTED -> "Your order " + event.orderId() + " could not be completed: " + event.reason();
            default -> null;
        };
        if (body == null) {
            log.warn("Unexpected event {}", event.type());
            return;
        }
        // A real implementation would call an email/SMS/push provider here.
        notifications.save(new Notification(event.orderId(), event.customerId(), event.type().name(), body));
        meters.counter("notifications.sent", "type", event.type().name()).increment();
        log.info("Notification to customer {}: {}", event.customerId(), body);
    }
}

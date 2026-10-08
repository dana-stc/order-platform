package dev.orderplatform.payment;

import dev.orderplatform.common.messaging.MessageCodec;
import dev.orderplatform.common.messaging.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCommandListener {

    private final MessageCodec codec;
    private final PaymentHandler handler;

    public PaymentCommandListener(MessageCodec codec, PaymentHandler handler) {
        this.codec = codec;
        this.handler = handler;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "payment-service")
    void onCommand(String payload) {
        handler.handle(codec.fromJson(payload));
    }
}

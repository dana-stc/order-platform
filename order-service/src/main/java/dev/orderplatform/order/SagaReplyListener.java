package dev.orderplatform.order;

import dev.orderplatform.common.messaging.MessageCodec;
import dev.orderplatform.common.messaging.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SagaReplyListener {

    private final MessageCodec codec;
    private final SagaOrchestrator orchestrator;

    public SagaReplyListener(MessageCodec codec, SagaOrchestrator orchestrator) {
        this.codec = codec;
        this.orchestrator = orchestrator;
    }

    @KafkaListener(topics = Topics.SAGA_REPLIES, groupId = "order-service")
    void onReply(String payload) {
        orchestrator.handle(codec.fromJson(payload));
    }
}

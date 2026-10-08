package dev.orderplatform.inventory;

import dev.orderplatform.common.messaging.MessageCodec;
import dev.orderplatform.common.messaging.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandListener {

    private final MessageCodec codec;
    private final InventoryHandler handler;

    public InventoryCommandListener(MessageCodec codec, InventoryHandler handler) {
        this.codec = codec;
        this.handler = handler;
    }

    @KafkaListener(topics = Topics.INVENTORY_COMMANDS, groupId = "inventory-service")
    void onCommand(String payload) {
        handler.handle(codec.fromJson(payload));
    }
}

package dev.orderplatform.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Explicit JSON (de)serialization: Kafka carries plain strings, so a malformed payload surfaces as an
 * {@link IllegalArgumentException} inside our listener (non-retryable -> straight to the DLT)
 * instead of poisoning the consumer in the deserializer.
 */
@Component
public class MessageCodec {

    private final ObjectMapper objectMapper;

    public MessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(SagaMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize " + message.type(), e);
        }
    }

    public SagaMessage fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, SagaMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed saga message: " + payload, e);
        }
    }
}

package dev.orderplatform.common.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent consumer. Kafka + outbox give at-least-once delivery, so every handler first records the
 * {@code messageId} in the SAME transaction as its side effects. A redelivered message hits the primary key,
 * {@code firstTime} returns false and the handler becomes a no-op.
 */
@Component
public class IdempotencyGuard {

    private final JdbcTemplate jdbc;

    public IdempotencyGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID messageId, String consumer) {
        return jdbc.update(
                "INSERT INTO processed_message (message_id, consumer) VALUES (?, ?) ON CONFLICT DO NOTHING",
                messageId, consumer) == 1;
    }
}

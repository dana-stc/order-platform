package dev.orderplatform.common.kafka;

import dev.orderplatform.common.messaging.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaPlatformConfig {

    @Bean
    NewTopic inventoryCommandsTopic(@Value("${platform.kafka.partitions:3}") int p,
                                    @Value("${platform.kafka.replicas:1}") int r) {
        return TopicBuilder.name(Topics.INVENTORY_COMMANDS).partitions(p).replicas(r).build();
    }

    @Bean
    NewTopic paymentCommandsTopic(@Value("${platform.kafka.partitions:3}") int p,
                                  @Value("${platform.kafka.replicas:1}") int r) {
        return TopicBuilder.name(Topics.PAYMENT_COMMANDS).partitions(p).replicas(r).build();
    }

    @Bean
    NewTopic sagaRepliesTopic(@Value("${platform.kafka.partitions:3}") int p,
                              @Value("${platform.kafka.replicas:1}") int r) {
        return TopicBuilder.name(Topics.SAGA_REPLIES).partitions(p).replicas(r).build();
    }

    @Bean
    NewTopic orderEventsTopic(@Value("${platform.kafka.partitions:3}") int p,
                              @Value("${platform.kafka.replicas:1}") int r) {
        return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(p).replicas(r).build();
    }

    /**
     * Retry transient failures with exponential backoff, then park the record on "&lt;topic&gt;.DLT" so one bad
     * message never blocks the partition. Malformed payloads (IllegalArgumentException) skip the retries.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template, (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxInterval(5_000);
        backOff.setMaxElapsedTime(20_000);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}

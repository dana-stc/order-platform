package dev.orderplatform.order;

import dev.orderplatform.common.messaging.MessageCodec;
import dev.orderplatform.common.messaging.MessageType;
import dev.orderplatform.common.messaging.SagaMessage;
import dev.orderplatform.common.messaging.Topics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Postgres + real Kafka. Plays the role of inventory/payment by sending replies to "saga.replies" and
 * checks the whole chain: HTTP -> DB + outbox -> Kafka command -> reply -> state machine -> Kafka event.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class OrderSagaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("management.otlp.tracing.endpoint", () -> "http://localhost:1/v1/traces");
        registry.add("outbox.poll-interval-ms", () -> "100");
    }

    @Autowired MockMvc mvc;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired MessageCodec codec;

    private static final String ORDER_BODY = """
            {"customerId":"c-1","items":[{"sku":"SKU-PHONE","quantity":2,"unitPrice":100.00}]}""";

    @Test
    void happyPath_endsConfirmed_andPublishesOrderConfirmed() throws Exception {
        UUID orderId = createOrder("happy-1");
        awaitCommand(Topics.INVENTORY_COMMANDS, orderId, MessageType.RESERVE_STOCK);

        reply(MessageType.STOCK_RESERVED, orderId);
        awaitCommand(Topics.PAYMENT_COMMANDS, orderId, MessageType.PROCESS_PAYMENT);

        reply(MessageType.PAYMENT_COMPLETED, orderId);
        awaitCommand(Topics.ORDER_EVENTS, orderId, MessageType.ORDER_CONFIRMED);
        assertStatus(orderId, "CONFIRMED");
    }

    @Test
    void paymentFailure_compensatesByReleasingStock_andEndsRejected() throws Exception {
        UUID orderId = createOrder("fail-1");
        awaitCommand(Topics.INVENTORY_COMMANDS, orderId, MessageType.RESERVE_STOCK);
        reply(MessageType.STOCK_RESERVED, orderId);
        awaitCommand(Topics.PAYMENT_COMMANDS, orderId, MessageType.PROCESS_PAYMENT);

        reply(MessageType.PAYMENT_FAILED, orderId);
        awaitCommand(Topics.INVENTORY_COMMANDS, orderId, MessageType.RELEASE_STOCK);

        reply(MessageType.STOCK_RELEASED, orderId);
        awaitCommand(Topics.ORDER_EVENTS, orderId, MessageType.ORDER_REJECTED);
        assertStatus(orderId, "REJECTED");
    }

    @Test
    void sameIdempotencyKey_returnsTheSameOrder() throws Exception {
        UUID first = createOrder("dup-1");
        String second = mvc.perform(post("/orders").header("Idempotency-Key", "dup-1")
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_BODY))
                .andExpect(status().isOk())          // 200 instead of 202: nothing new was created
                .andReturn().getResponse().getContentAsString();
        assertThat(second).contains(first.toString());
    }

    @Test
    void duplicatedReply_isProcessedOnlyOnce() throws Exception {
        UUID orderId = createOrder("redelivery-1");
        awaitCommand(Topics.INVENTORY_COMMANDS, orderId, MessageType.RESERVE_STOCK);

        SagaMessage reserved = SagaMessage.of(MessageType.STOCK_RESERVED, orderId);
        kafkaTemplate.send(Topics.SAGA_REPLIES, orderId.toString(), codec.toJson(reserved));
        kafkaTemplate.send(Topics.SAGA_REPLIES, orderId.toString(), codec.toJson(reserved));  // redelivery

        awaitCommand(Topics.PAYMENT_COMMANDS, orderId, MessageType.PROCESS_PAYMENT);
        assertStatus(orderId, "STOCK_RESERVED");
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private UUID createOrder(String key) throws Exception {
        String json = mvc.perform(post("/orders").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_BODY))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private void reply(MessageType type, UUID orderId) {
        kafkaTemplate.send(Topics.SAGA_REPLIES, orderId.toString(), codec.toJson(SagaMessage.of(type, orderId)));
    }

    private void assertStatus(UUID orderId, String expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/orders/" + orderId)).andExpect(jsonPath("$.status").value(expected)));
    }

    private void awaitCommand(String topic, UUID orderId, MessageType type) {
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), "test-" + UUID.randomUUID(), "true"),
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(java.util.List.of(topic));
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                for (ConsumerRecord<String, String> r : KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500))) {
                    SagaMessage m = codec.fromJson(r.value());
                    if (m.orderId().equals(orderId) && m.type() == type) {
                        return true;
                    }
                }
                return false;
            });
        }
    }
}

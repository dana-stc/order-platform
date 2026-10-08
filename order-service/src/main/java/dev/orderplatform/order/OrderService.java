package dev.orderplatform.order;

import dev.orderplatform.common.messaging.MessageType;
import dev.orderplatform.common.messaging.SagaMessage;
import dev.orderplatform.common.messaging.Topics;
import dev.orderplatform.common.outbox.OutboxService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final OutboxService outbox;
    private final TransactionTemplate tx;
    private final Counter created;

    public OrderService(OrderRepository orders, OutboxService outbox, TransactionTemplate tx, MeterRegistry meters) {
        this.orders = orders;
        this.outbox = outbox;
        this.tx = tx;
        this.created = Counter.builder("orders.accepted").description("Orders accepted by the API").register(meters);
    }

    /**
     * Starts the saga: persists the order and, in the same transaction, the first command (RESERVE_STOCK)
     * in the outbox. The HTTP caller gets 202 immediately; the rest happens asynchronously.
     * <p>
     * A repeated request with the same Idempotency-Key returns the original order instead of creating a duplicate.
     */
    public CreateResult create(OrderDtos.CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = orders.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return new CreateResult(existing.get(), false);
            }
        }
        try {
            Order order = tx.execute(status -> {
                List<OrderLine> lines = request.items().stream()
                        .map(i -> new OrderLine(i.sku(), i.quantity(), i.unitPrice()))
                        .toList();
                Order saved = orders.save(new Order(request.customerId(), lines, idempotencyKey));
                outbox.enqueue(Topics.INVENTORY_COMMANDS, SagaMessage.of(MessageType.RESERVE_STOCK, saved.getId())
                        .withCustomerId(saved.getCustomerId())
                        .withAmount(saved.getTotalAmount())
                        .withItems(lines.stream().map(l -> new SagaMessage.Item(l.getSku(), l.getQuantity())).toList()));
                return saved;
            });
            created.increment();
            log.info("Order {} created for customer {} (total {})", order.getId(), order.getCustomerId(),
                    order.getTotalAmount());
            return new CreateResult(order, true);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests with the same key: the unique index let only one through.
            if (idempotencyKey != null) {
                return orders.findByIdempotencyKey(idempotencyKey)
                        .map(o -> new CreateResult(o, false))
                        .orElseThrow(() -> e);
            }
            throw e;
        }
    }

    public record CreateResult(Order order, boolean created) {
    }
}

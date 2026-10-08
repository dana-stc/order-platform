package dev.orderplatform.inventory;

import dev.orderplatform.common.messaging.IdempotencyGuard;
import dev.orderplatform.common.messaging.MessageType;
import dev.orderplatform.common.messaging.SagaMessage;
import dev.orderplatform.common.messaging.Topics;
import dev.orderplatform.common.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class InventoryHandler {

    static final String CONSUMER = "inventory-service";
    private static final Logger log = LoggerFactory.getLogger(InventoryHandler.class);

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final OutboxService outbox;
    private final IdempotencyGuard idempotency;

    public InventoryHandler(ProductRepository products, ReservationRepository reservations, OutboxService outbox,
                            IdempotencyGuard idempotency) {
        this.products = products;
        this.reservations = reservations;
        this.outbox = outbox;
        this.idempotency = idempotency;
    }

    @Transactional
    public void handle(SagaMessage command) {
        if (!idempotency.firstTime(command.messageId(), CONSUMER)) {
            log.info("Duplicate {} {} ignored", command.type(), command.messageId());
            return;
        }
        switch (command.type()) {
            case RESERVE_STOCK -> reserve(command);
            case RELEASE_STOCK -> release(command);
            default -> log.warn("Unexpected command {}", command.type());
        }
    }

    private void reserve(SagaMessage command) {
        // Merge duplicate SKUs; TreeMap keeps a deterministic order.
        Map<String, Integer> wanted = command.items().stream().collect(
                Collectors.toMap(SagaMessage.Item::sku, SagaMessage.Item::quantity, Integer::sum, TreeMap::new));

        Map<String, Product> locked = products.lockBySkus(wanted.keySet()).stream()
                .collect(Collectors.toMap(Product::getSku, p -> p));

        for (var entry : wanted.entrySet()) {
            Product product = locked.get(entry.getKey());
            if (product == null) {
                reply(command, MessageType.STOCK_REJECTED, "Unknown product " + entry.getKey());
                return;
            }
            if (product.getAvailable() < entry.getValue()) {
                reply(command, MessageType.STOCK_REJECTED, "Insufficient stock for " + entry.getKey());
                return;
            }
        }

        wanted.forEach((sku, quantity) -> {
            locked.get(sku).reserve(quantity);
            reservations.save(new Reservation(command.orderId(), sku, quantity));
        });
        log.info("Stock reserved for order {}: {}", command.orderId(), wanted);
        reply(command, MessageType.STOCK_RESERVED, null);
    }

    /** Compensation. Always replies (even with nothing to release) so the saga can finish. */
    private void release(SagaMessage command) {
        List<Reservation> active = reservations.findByOrderIdAndStatus(command.orderId(), Reservation.Status.RESERVED);
        if (!active.isEmpty()) {
            Map<String, Product> locked = products
                    .lockBySkus(active.stream().map(Reservation::getSku).toList()).stream()
                    .collect(Collectors.toMap(Product::getSku, p -> p));
            for (Reservation r : active) {
                locked.get(r.getSku()).release(r.getQuantity());
                r.markReleased();
            }
        }
        log.info("Stock released for order {} ({} reservations)", command.orderId(), active.size());
        reply(command, MessageType.STOCK_RELEASED, null);
    }

    private void reply(SagaMessage command, MessageType type, String reason) {
        outbox.enqueue(Topics.SAGA_REPLIES, SagaMessage.of(type, command.orderId()).withReason(reason));
    }
}

package dev.orderplatform.inventory;

import dev.orderplatform.common.messaging.IdempotencyGuard;
import dev.orderplatform.common.messaging.MessageType;
import dev.orderplatform.common.messaging.SagaMessage;
import dev.orderplatform.common.messaging.Topics;
import dev.orderplatform.common.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryHandlerTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final IdempotencyGuard idempotency = mock(IdempotencyGuard.class);
    private final InventoryHandler handler = new InventoryHandler(products, reservations, outbox, idempotency);

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(idempotency.firstTime(any(), anyString())).thenReturn(true);
    }

    @Test
    void reserve_decrementsStock_andReplies() {
        Product phone = new Product("SKU-PHONE", 10);
        when(products.lockBySkus(any())).thenReturn(List.of(phone));

        handler.handle(reserve(new SagaMessage.Item("SKU-PHONE", 3), new SagaMessage.Item("SKU-PHONE", 2)));

        assertThat(phone.getAvailable()).isEqualTo(5);   // duplicate SKUs are merged: 3 + 2
        verify(reservations).save(any(Reservation.class));
        assertThat(reply().type()).isEqualTo(MessageType.STOCK_RESERVED);
    }

    @Test
    void reserve_withInsufficientStock_rejects_andChangesNothing() {
        Product phone = new Product("SKU-PHONE", 1);
        Product mouse = new Product("SKU-MOUSE", 100);
        when(products.lockBySkus(any())).thenReturn(List.of(mouse, phone));

        handler.handle(reserve(new SagaMessage.Item("SKU-MOUSE", 5), new SagaMessage.Item("SKU-PHONE", 2)));

        assertThat(phone.getAvailable()).isEqualTo(1);
        assertThat(mouse.getAvailable()).isEqualTo(100);   // all-or-nothing
        verify(reservations, never()).save(any());
        SagaMessage reply = reply();
        assertThat(reply.type()).isEqualTo(MessageType.STOCK_REJECTED);
        assertThat(reply.reason()).contains("SKU-PHONE");
    }

    @Test
    void reserve_withUnknownProduct_rejects() {
        when(products.lockBySkus(any())).thenReturn(List.of());

        handler.handle(reserve(new SagaMessage.Item("SKU-NOPE", 1)));

        assertThat(reply().type()).isEqualTo(MessageType.STOCK_REJECTED);
    }

    @Test
    void release_restoresStock_andReplies() {
        Product phone = new Product("SKU-PHONE", 5);
        Reservation reservation = new Reservation(orderId, "SKU-PHONE", 3);
        when(reservations.findByOrderIdAndStatus(orderId, Reservation.Status.RESERVED)).thenReturn(List.of(reservation));
        when(products.lockBySkus(any())).thenReturn(List.of(phone));

        handler.handle(SagaMessage.of(MessageType.RELEASE_STOCK, orderId));

        assertThat(phone.getAvailable()).isEqualTo(8);
        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.RELEASED);
        assertThat(reply().type()).isEqualTo(MessageType.STOCK_RELEASED);
    }

    @Test
    void release_withNothingReserved_stillReplies_soTheSagaCanFinish() {
        when(reservations.findByOrderIdAndStatus(orderId, Reservation.Status.RESERVED)).thenReturn(List.of());

        handler.handle(SagaMessage.of(MessageType.RELEASE_STOCK, orderId));

        assertThat(reply().type()).isEqualTo(MessageType.STOCK_RELEASED);
    }

    @Test
    void duplicateCommand_isIgnored() {
        SagaMessage command = reserve(new SagaMessage.Item("SKU-PHONE", 1));
        when(idempotency.firstTime(command.messageId(), InventoryHandler.CONSUMER)).thenReturn(false);

        handler.handle(command);

        verify(outbox, never()).enqueue(anyString(), any());
    }

    private SagaMessage reserve(SagaMessage.Item... items) {
        return SagaMessage.of(MessageType.RESERVE_STOCK, orderId).withItems(List.of(items));
    }

    private SagaMessage reply() {
        ArgumentCaptor<SagaMessage> captor = ArgumentCaptor.forClass(SagaMessage.class);
        verify(outbox).enqueue(eq(Topics.SAGA_REPLIES), captor.capture());
        return captor.getValue();
    }
}

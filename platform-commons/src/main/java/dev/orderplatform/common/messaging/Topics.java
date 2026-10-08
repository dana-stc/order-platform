package dev.orderplatform.common.messaging;

public final class Topics {

    /** Orchestrator -> inventory: RESERVE_STOCK, RELEASE_STOCK (compensation). */
    public static final String INVENTORY_COMMANDS = "inventory.commands";
    /** Orchestrator -> payment: PROCESS_PAYMENT. */
    public static final String PAYMENT_COMMANDS = "payment.commands";
    /** Participants -> orchestrator: every reply of the saga. */
    public static final String SAGA_REPLIES = "saga.replies";
    /** Order lifecycle facts for everybody else: ORDER_CONFIRMED, ORDER_REJECTED. */
    public static final String ORDER_EVENTS = "order.events";

    private Topics() {
    }
}

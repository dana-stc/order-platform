package dev.orderplatform.common.messaging;

public enum MessageType {
    // commands (orchestrator -> participants)
    RESERVE_STOCK,
    RELEASE_STOCK,
    PROCESS_PAYMENT,
    // replies (participants -> orchestrator)
    STOCK_RESERVED,
    STOCK_REJECTED,
    STOCK_RELEASED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    // events (order lifecycle)
    ORDER_CONFIRMED,
    ORDER_REJECTED
}

package dev.orderplatform.order;

public enum OrderStatus {
    /** Created, waiting for the stock reservation. */
    PENDING,
    /** Stock reserved, waiting for the payment. */
    STOCK_RESERVED,
    /** Payment failed, stock is being released (compensation in progress). */
    COMPENSATING,
    /** Final: saga completed successfully. */
    CONFIRMED,
    /** Final: saga failed and every completed step was compensated. */
    REJECTED;

    public boolean isFinal() {
        return this == CONFIRMED || this == REJECTED;
    }
}

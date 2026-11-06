package com.ecommerce.order.domain;

public enum OrderStatus {
    /** Accepted and priced. Stock is not held yet. */
    CREATED,
    /** Inventory accepted the reservation. */
    INVENTORY_RESERVED,
    /** A charge has been requested. */
    PAYMENT_PENDING,
    /** Payment captured and stock committed. */
    CONFIRMED,
    /** Reservation or payment failed. Nothing is held. */
    FAILED,
    /** Cancelled before payment was captured. */
    CANCELLED,
    /** A confirmed order was refunded. */
    REFUNDED
}

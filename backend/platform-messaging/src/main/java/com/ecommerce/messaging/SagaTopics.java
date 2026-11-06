package com.ecommerce.messaging;

/** Kafka topic names. One topic per event type. The record key is the order id. */
public final class SagaTopics {

    public static final String ORDER_CREATED = "order-created";
    public static final String INVENTORY_RESERVED = "inventory-reserved";
    public static final String INVENTORY_REJECTED = "inventory-rejected";
    public static final String PAYMENT_REQUESTED = "payment-requested";
    public static final String PAYMENT_COMPLETED = "payment-completed";
    public static final String PAYMENT_FAILED = "payment-failed";
    public static final String ORDER_CONFIRMED = "order-confirmed";
    public static final String ORDER_CANCELLED = "order-cancelled";
    public static final String INVENTORY_RELEASED = "inventory-released";
    public static final String PAYMENT_REFUNDED = "payment-refunded";

    private SagaTopics() {
    }
}

package com.ecommerce.messaging;

import java.math.BigDecimal;
import java.util.List;

/** JSON bodies carried inside {@link SagaEvent#payload()}. */
public final class SagaPayloads {

    private SagaPayloads() {
    }

    public record OrderLinePayload(long productId, int quantity) {
    }

    public record OrderCreated(long orderId, long userId, List<OrderLinePayload> lines) {
    }

    public record InventoryReserved(long orderId) {
    }

    public record InventoryRejected(long orderId, String reason) {
    }

    public record PaymentRequested(long orderId, long userId, BigDecimal amount, String currency) {
    }

    public record PaymentCompleted(long orderId, long userId, BigDecimal amount, String currency) {
    }

    public record PaymentFailed(long orderId, long userId, String failureCode) {
    }

    public record OrderConfirmed(long orderId, long userId) {
    }

    public record OrderCancelled(long orderId, long userId, String reason, boolean refundRequested) {
    }

    public record InventoryReleased(long orderId) {
    }

    public record PaymentRefunded(long orderId, long userId) {
    }
}

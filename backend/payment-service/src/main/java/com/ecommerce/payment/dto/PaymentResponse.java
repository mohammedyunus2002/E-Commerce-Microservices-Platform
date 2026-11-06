package com.ecommerce.payment.dto;

import com.ecommerce.payment.domain.Payment;
import com.ecommerce.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        Long userId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getUserId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus(), payment.getFailureCode(), payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}

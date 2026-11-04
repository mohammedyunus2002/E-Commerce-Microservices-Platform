package com.ecommerce.inventory.dto;

import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.StockReservation;

import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long orderId,
        Long productId,
        int quantity,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static ReservationResponse from(StockReservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getOrderId(), reservation.getProductId(),
                reservation.getQuantity(), reservation.getStatus(), reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}

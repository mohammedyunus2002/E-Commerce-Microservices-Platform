package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** One order line to reserve: product and number of units. */
public record ReservationLine(
        @NotNull @Positive Long productId,
        @Positive int quantity) {
}

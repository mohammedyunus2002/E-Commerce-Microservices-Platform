package com.ecommerce.payment.dto;

import jakarta.validation.constraints.NotNull;

/** The amount is the order total. The client cannot choose a different amount. */
public record ChargeRequest(@NotNull Long orderId) {
}

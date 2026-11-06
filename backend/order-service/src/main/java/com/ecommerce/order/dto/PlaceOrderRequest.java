package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Prices are taken from the catalog. A client-supplied price is ignored because this request has none. */
public record PlaceOrderRequest(
        @NotEmpty @Size(max = 50) List<@Valid OrderLineRequest> lines) {
}

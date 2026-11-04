package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Sets the sellable quantity outright, e.g. after a stock count. Reserved units are untouched. */
public record SetStockRequest(
        @NotNull @Min(0) @Max(1_000_000) Integer availableQuantity) {
}

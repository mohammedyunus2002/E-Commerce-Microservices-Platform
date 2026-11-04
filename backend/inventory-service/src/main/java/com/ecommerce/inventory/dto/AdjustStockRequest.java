package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Relative change such as a delivery (+) or damaged goods (-). The reason is written to the audit log. */
public record AdjustStockRequest(
        @NotNull @Min(-1_000_000) @Max(1_000_000) Integer delta,
        @NotBlank @Size(max = 200) String reason) {
}

package com.ecommerce.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

/**
 * Full replacement of the editable fields. The SKU is the product's business key and never changes.
 */
public record UpdateProductRequest(
        @NotBlank @Size(max = 200)
        String name,

        @Size(max = 4000)
        String description,

        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @Size(max = 500) @URL(regexp = "^https?://.*", message = "must be an http or https URL")
        String imageUrl,

        @NotNull @Positive
        Long categoryId,

        @NotNull
        Boolean active) {
}

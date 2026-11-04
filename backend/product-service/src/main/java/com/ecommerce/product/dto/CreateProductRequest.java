package com.ecommerce.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{2,39}$",
                message = "must be 3 to 40 uppercase letters, digits, or hyphens")
        String sku,

        @NotBlank @Size(max = 200)
        String name,

        @Size(max = 4000)
        String description,

        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @Size(max = 500) @URL(regexp = "^https?://.*", message = "must be an http or https URL")
        String imageUrl,

        @NotNull @Positive
        Long categoryId) {
}

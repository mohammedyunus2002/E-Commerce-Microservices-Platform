package com.ecommerce.inventory.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.product-service")
public record ProductServiceProperties(
        @NotBlank String url,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {
}

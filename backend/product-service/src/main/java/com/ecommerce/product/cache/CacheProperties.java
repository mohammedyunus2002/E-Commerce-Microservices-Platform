package com.ecommerce.product.cache;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(@NotNull Duration productTtl, @NotNull Duration categoryTtl) {
}

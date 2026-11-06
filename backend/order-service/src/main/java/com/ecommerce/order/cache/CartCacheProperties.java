package com.ecommerce.order.cache;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.cart")
public record CartCacheProperties(boolean cacheEnabled, @NotNull Duration cacheTtl) {
}

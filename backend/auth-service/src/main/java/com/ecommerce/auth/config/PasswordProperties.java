package com.ecommerce.auth.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param bcryptStrength log2 cost factor; 12 in production, lowered only in tests for speed
 */
@Validated
@ConfigurationProperties(prefix = "security.password")
public record PasswordProperties(@Min(4) @Max(16) int bcryptStrength) {
}

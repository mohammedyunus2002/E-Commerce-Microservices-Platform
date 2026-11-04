package com.ecommerce.gateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @param jwtSecret          same HMAC key the auth-service signs with ({@code JWT_SECRET})
 * @param jwtIssuer          expected {@code iss} claim
 * @param corsAllowedOrigins browser origins allowed to call the API
 */
@Validated
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(
        @NotBlank String jwtSecret,
        @NotBlank String jwtIssuer,
        @NotEmpty List<String> corsAllowedOrigins) {

    private static final int MIN_SECRET_BYTES = 32;

    public GatewaySecurityProperties {
        if (jwtSecret != null && jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "gateway.security.jwt-secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
    }

    public byte[] jwtSecretBytes() {
        return jwtSecret.getBytes(StandardCharsets.UTF_8);
    }
}

package com.ecommerce.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Same JSON shape as the services' {@code ApiError}, so clients parse gateway and service errors alike.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GatewayError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId) {
}

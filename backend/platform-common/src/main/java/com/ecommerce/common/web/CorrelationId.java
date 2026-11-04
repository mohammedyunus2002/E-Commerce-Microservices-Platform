package com.ecommerce.common.web;

import org.slf4j.MDC;

/**
 * Header and MDC key used to trace one request across the gateway, services, and Kafka events.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}

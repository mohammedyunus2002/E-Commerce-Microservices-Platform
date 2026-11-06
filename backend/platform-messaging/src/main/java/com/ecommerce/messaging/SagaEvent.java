package com.ecommerce.messaging;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Wire format for every saga message. {@code correlationId} is the order id.
 * {@code type} equals the Kafka topic. The payload is plain JSON with no type hints.
 */
public record SagaEvent(
        String eventId,
        String correlationId,
        String causationId,
        String type,
        JsonNode payload) {
}

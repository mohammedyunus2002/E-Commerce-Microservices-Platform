package com.ecommerce.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SagaJson {

    private final ObjectMapper objectMapper;

    public SagaJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode toNode(Object value) {
        return objectMapper.valueToTree(value);
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not write saga event", e);
        }
    }

    public SagaEvent readEvent(String json) {
        try {
            SagaEvent event = objectMapper.readValue(json, SagaEvent.class);
            if (event.eventId() == null || event.type() == null || event.correlationId() == null
                    || event.payload() == null) {
                throw new IllegalArgumentException("Saga event is missing eventId, type, correlationId, or payload");
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Saga event is not valid JSON", e);
        }
    }

    public <T> T readPayload(SagaEvent event, Class<T> type) {
        try {
            return objectMapper.treeToValue(event.payload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Saga payload does not match " + type.getSimpleName(), e);
        }
    }
}

package com.ecommerce.messaging;

import java.time.Clock;
import java.util.UUID;

/** Call inside the business transaction so the event and the state change commit together. */
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final SagaJson sagaJson;
    private final Clock clock;

    public OutboxWriter(OutboxEventRepository repository, SagaJson sagaJson, Clock clock) {
        this.repository = repository;
        this.sagaJson = sagaJson;
        this.clock = clock;
    }

    public String enqueue(String topic, long orderId, String causationId, Object payload) {
        String eventId = UUID.randomUUID().toString();
        SagaEvent event = new SagaEvent(eventId, Long.toString(orderId), causationId, topic, sagaJson.toNode(payload));
        repository.save(new OutboxEvent(eventId, topic, Long.toString(orderId), sagaJson.write(event), causationId,
                clock.instant()));
        return eventId;
    }
}

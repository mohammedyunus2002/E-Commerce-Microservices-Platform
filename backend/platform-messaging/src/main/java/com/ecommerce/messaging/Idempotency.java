package com.ecommerce.messaging;

import java.time.Clock;

/** Returns false when this event id was already committed. Call inside the side-effect transaction. */
public class Idempotency {

    private final ProcessedEventRepository repository;
    private final Clock clock;

    public Idempotency(ProcessedEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean firstTime(String eventId, String topic) {
        if (repository.existsById(eventId)) {
            return false;
        }
        repository.save(new ProcessedEvent(eventId, topic, clock.instant()));
        return true;
    }
}

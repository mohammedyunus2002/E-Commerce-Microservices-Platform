package com.ecommerce.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One row commits with the business change. A relay publishes it and then sets {@code publishedAt}. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String topic;

    @Column(name = "aggregate_key", nullable = false, length = 64)
    private String aggregateKey;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(name = "causation_id", length = 36)
    private String causationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String id, String topic, String aggregateKey, String payload, String causationId,
                       Instant createdAt) {
        this.id = id;
        this.topic = topic;
        this.aggregateKey = aggregateKey;
        this.payload = payload;
        this.causationId = causationId;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant when) {
        this.publishedAt = when;
    }

    public String getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}

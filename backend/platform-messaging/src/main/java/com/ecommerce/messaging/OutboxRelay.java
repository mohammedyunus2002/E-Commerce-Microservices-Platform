package com.ecommerce.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Publishes unpublished outbox rows in creation order. A crash after the send publishes the same event id again. */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<?, ?> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<?, ?> kafkaTemplate,
                       PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.saga.outbox-delay:1s}", initialDelayString = "${app.saga.outbox-delay:1s}")
    public void publishBatch() {
        List<OutboxEvent> batch = repository.findUnpublished(PageRequest.of(0, 20));
        for (OutboxEvent event : batch) {
            try {
                send(event).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Outbox publish interrupted at {} {}", event.getTopic(), event.getId());
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                log.warn("Outbox publish failed for {} {}: {}", event.getTopic(), event.getId(), e.toString());
                return;
            }
            transactionTemplate.executeWithoutResult(status -> repository.findById(event.getId()).ifPresent(row -> {
                if (row.getPublishedAt() == null) {
                    row.markPublished(clock.instant());
                }
            }));
            log.info("Published {} {} for order {}", event.getTopic(), event.getId(), event.getAggregateKey());
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.concurrent.Future<SendResult<String, String>> send(OutboxEvent event) {
        return ((KafkaTemplate<String, String>) kafkaTemplate)
                .send(event.getTopic(), event.getAggregateKey(), event.getPayload());
    }
}

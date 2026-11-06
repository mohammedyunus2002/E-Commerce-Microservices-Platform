package com.ecommerce.inventory.saga;

import com.ecommerce.messaging.SagaAutoConfiguration.SagaConsumer;
import com.ecommerce.messaging.SagaTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true")
public class InventorySagaListener {

    private final SagaConsumer consumer;
    private final InventorySaga saga;

    public InventorySagaListener(SagaConsumer consumer, InventorySaga saga) {
        this.consumer = consumer;
        this.saga = saga;
    }

    @KafkaListener(topics = {SagaTopics.ORDER_CREATED, SagaTopics.ORDER_CANCELLED, SagaTopics.ORDER_CONFIRMED},
            containerFactory = "sagaListenerFactory")
    public void onEvent(String json) {
        consumer.consume(json, event -> {
            switch (event.type()) {
                case SagaTopics.ORDER_CREATED -> saga.onOrderCreated(event);
                case SagaTopics.ORDER_CANCELLED -> saga.onOrderCancelled(event);
                case SagaTopics.ORDER_CONFIRMED -> saga.onOrderConfirmed(event);
                default -> throw new IllegalArgumentException("Unexpected event type " + event.type());
            }
        });
    }
}

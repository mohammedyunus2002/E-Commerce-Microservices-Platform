package com.ecommerce.order.saga;

import com.ecommerce.messaging.SagaAutoConfiguration.SagaConsumer;
import com.ecommerce.messaging.SagaTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true")
public class OrderSagaListener {

    private final SagaConsumer consumer;
    private final OrderSaga saga;

    public OrderSagaListener(SagaConsumer consumer, OrderSaga saga) {
        this.consumer = consumer;
        this.saga = saga;
    }

    @KafkaListener(topics = {
            SagaTopics.INVENTORY_RESERVED,
            SagaTopics.INVENTORY_REJECTED,
            SagaTopics.PAYMENT_COMPLETED,
            SagaTopics.PAYMENT_FAILED,
            SagaTopics.INVENTORY_RELEASED,
            SagaTopics.PAYMENT_REFUNDED
    }, containerFactory = "sagaListenerFactory")
    public void onEvent(String json) {
        consumer.consume(json, event -> {
            switch (event.type()) {
                case SagaTopics.INVENTORY_RESERVED -> saga.onInventoryReserved(event);
                case SagaTopics.INVENTORY_REJECTED -> saga.onInventoryRejected(event);
                case SagaTopics.PAYMENT_COMPLETED -> saga.onPaymentCompleted(event);
                case SagaTopics.PAYMENT_FAILED -> saga.onPaymentFailed(event);
                case SagaTopics.INVENTORY_RELEASED -> saga.onInventoryReleased(event);
                case SagaTopics.PAYMENT_REFUNDED -> saga.onPaymentRefunded(event);
                default -> throw new IllegalArgumentException("Unexpected event type " + event.type());
            }
        });
    }
}

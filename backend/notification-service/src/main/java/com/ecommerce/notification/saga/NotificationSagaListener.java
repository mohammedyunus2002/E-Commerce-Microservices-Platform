package com.ecommerce.notification.saga;

import com.ecommerce.messaging.SagaAutoConfiguration.SagaConsumer;
import com.ecommerce.messaging.SagaTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true")
public class NotificationSagaListener {

    private final SagaConsumer consumer;
    private final NotificationSaga saga;

    public NotificationSagaListener(SagaConsumer consumer, NotificationSaga saga) {
        this.consumer = consumer;
        this.saga = saga;
    }

    @KafkaListener(topics = {
            SagaTopics.PAYMENT_COMPLETED,
            SagaTopics.ORDER_CONFIRMED,
            SagaTopics.ORDER_CANCELLED,
            SagaTopics.PAYMENT_FAILED,
            SagaTopics.PAYMENT_REFUNDED
    }, containerFactory = "sagaListenerFactory")
    public void onEvent(String json) {
        consumer.consume(json, event -> {
            switch (event.type()) {
                case SagaTopics.PAYMENT_COMPLETED -> saga.onPaymentCompleted(event);
                case SagaTopics.ORDER_CONFIRMED -> saga.onOrderConfirmed(event);
                case SagaTopics.ORDER_CANCELLED -> saga.onOrderCancelled(event);
                case SagaTopics.PAYMENT_FAILED -> saga.onPaymentFailed(event);
                case SagaTopics.PAYMENT_REFUNDED -> saga.onPaymentRefunded(event);
                default -> throw new IllegalArgumentException("Unexpected event type " + event.type());
            }
        });
    }
}

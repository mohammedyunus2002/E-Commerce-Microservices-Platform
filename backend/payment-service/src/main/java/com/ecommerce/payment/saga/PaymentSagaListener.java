package com.ecommerce.payment.saga;

import com.ecommerce.messaging.SagaAutoConfiguration.SagaConsumer;
import com.ecommerce.messaging.SagaTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true")
public class PaymentSagaListener {

    private final SagaConsumer consumer;
    private final PaymentSaga saga;

    public PaymentSagaListener(SagaConsumer consumer, PaymentSaga saga) {
        this.consumer = consumer;
        this.saga = saga;
    }

    @KafkaListener(topics = {SagaTopics.PAYMENT_REQUESTED, SagaTopics.ORDER_CANCELLED},
            containerFactory = "sagaListenerFactory")
    public void onEvent(String json) {
        consumer.consume(json, event -> {
            switch (event.type()) {
                case SagaTopics.PAYMENT_REQUESTED -> saga.onPaymentRequested(event);
                case SagaTopics.ORDER_CANCELLED -> saga.onOrderCancelled(event);
                default -> throw new IllegalArgumentException("Unexpected event type " + event.type());
            }
        });
    }
}

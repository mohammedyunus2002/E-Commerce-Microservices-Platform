package com.ecommerce.order.saga;

import com.ecommerce.messaging.OutboxWriter;
import com.ecommerce.messaging.SagaPayloads.OrderCancelled;
import com.ecommerce.messaging.SagaPayloads.OrderCreated;
import com.ecommerce.messaging.SagaPayloads.OrderLinePayload;
import com.ecommerce.messaging.SagaTopics;
import com.ecommerce.order.domain.CustomerOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OrderEventsConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true")
    public OrderEvents kafkaOrderEvents(OutboxWriter outboxWriter) {
        return new OrderEvents() {
            @Override
            public boolean sagaEnabled() {
                return true;
            }

            @Override
            public void orderCreated(CustomerOrder order) {
                List<OrderLinePayload> lines = order.getItems().stream()
                        .map(item -> new OrderLinePayload(item.getProductId(), item.getQuantity()))
                        .toList();
                outboxWriter.enqueue(SagaTopics.ORDER_CREATED, order.getId(), null,
                        new OrderCreated(order.getId(), order.getUserId(), lines));
            }

            @Override
            public void orderCancelled(CustomerOrder order, String reason, boolean refundRequested) {
                outboxWriter.enqueue(SagaTopics.ORDER_CANCELLED, order.getId(), null,
                        new OrderCancelled(order.getId(), order.getUserId(), reason, refundRequested));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "false", matchIfMissing = true)
    public OrderEvents noopOrderEvents() {
        return new OrderEvents() {
            @Override
            public boolean sagaEnabled() {
                return false;
            }

            @Override
            public void orderCreated(CustomerOrder order) {
            }

            @Override
            public void orderCancelled(CustomerOrder order, String reason, boolean refundRequested) {
            }
        };
    }
}

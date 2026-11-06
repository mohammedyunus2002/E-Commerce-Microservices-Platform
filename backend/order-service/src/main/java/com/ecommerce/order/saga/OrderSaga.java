package com.ecommerce.order.saga;

import com.ecommerce.messaging.Idempotency;
import com.ecommerce.messaging.OutboxWriter;
import com.ecommerce.messaging.SagaEvent;
import com.ecommerce.messaging.SagaJson;
import com.ecommerce.messaging.SagaPayloads.InventoryRejected;
import com.ecommerce.messaging.SagaPayloads.InventoryReserved;
import com.ecommerce.messaging.SagaPayloads.OrderCancelled;
import com.ecommerce.messaging.SagaPayloads.OrderConfirmed;
import com.ecommerce.messaging.SagaPayloads.PaymentCompleted;
import com.ecommerce.messaging.SagaPayloads.PaymentFailed;
import com.ecommerce.messaging.SagaPayloads.PaymentRefunded;
import com.ecommerce.messaging.SagaPayloads.PaymentRequested;
import com.ecommerce.messaging.SagaTopics;
import com.ecommerce.order.domain.CustomerOrder;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.OrderStatusChange;
import com.ecommerce.order.repository.CustomerOrderRepository;
import com.ecommerce.order.repository.OrderStatusChangeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true")
public class OrderSaga {

    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);
    private static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    private static final String CUSTOMER_CANCEL = "CUSTOMER_CANCEL";

    private final CustomerOrderRepository orderRepository;
    private final OrderStatusChangeRepository historyRepository;
    private final Idempotency idempotency;
    private final OutboxWriter outboxWriter;
    private final SagaJson sagaJson;
    private final Clock clock;

    public OrderSaga(CustomerOrderRepository orderRepository, OrderStatusChangeRepository historyRepository,
                     Idempotency idempotency, OutboxWriter outboxWriter, SagaJson sagaJson, Clock clock) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.idempotency = idempotency;
        this.outboxWriter = outboxWriter;
        this.sagaJson = sagaJson;
        this.clock = clock;
    }

    @Transactional
    public void onInventoryReserved(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        InventoryReserved reserved = sagaJson.readPayload(event, InventoryReserved.class);
        CustomerOrder order = orderRepository.findWithItemsById(reserved.orderId()).orElse(null);
        if (order == null) {
            log.warn("inventory-reserved for unknown order {}", reserved.orderId());
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.isCancelRequested()) {
            outboxWriter.enqueue(SagaTopics.ORDER_CANCELLED, order.getId(), event.eventId(),
                    new OrderCancelled(order.getId(), order.getUserId(), CUSTOMER_CANCEL, false));
            return;
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            log.info("Order {} is {}; ignoring inventory-reserved", order.getId(), order.getStatus());
            return;
        }
        Instant now = clock.instant();
        OrderStatus from = order.getStatus();
        order.reserveInventory(now);
        historyRepository.save(new OrderStatusChange(order, from, order.getStatus(), "inventory reserved", now));
        from = order.getStatus();
        order.requestPayment(now);
        historyRepository.save(new OrderStatusChange(order, from, order.getStatus(), "payment requested", now));
        outboxWriter.enqueue(SagaTopics.PAYMENT_REQUESTED, order.getId(), event.eventId(),
                new PaymentRequested(order.getId(), order.getUserId(), order.getTotalAmount(), order.getCurrency()));
    }

    @Transactional
    public void onInventoryRejected(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        InventoryRejected rejected = sagaJson.readPayload(event, InventoryRejected.class);
        CustomerOrder order = require(rejected.orderId());
        if (order.getStatus() != OrderStatus.CREATED) {
            log.info("Order {} is {}; ignoring inventory-rejected", order.getId(), order.getStatus());
            return;
        }
        Instant now = clock.instant();
        OrderStatus from = order.getStatus();
        order.fail(now);
        historyRepository.save(new OrderStatusChange(order, from, order.getStatus(), rejected.reason(), now));
    }

    @Transactional
    public void onPaymentCompleted(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        PaymentCompleted completed = sagaJson.readPayload(event, PaymentCompleted.class);
        CustomerOrder order = require(completed.orderId());
        if (order.getStatus() == OrderStatus.CANCELLED || order.isCancelRequested()) {
            outboxWriter.enqueue(SagaTopics.ORDER_CANCELLED, order.getId(), event.eventId(),
                    new OrderCancelled(order.getId(), order.getUserId(), CUSTOMER_CANCEL, true));
            return;
        }
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            log.info("Order {} is {}; ignoring payment-completed", order.getId(), order.getStatus());
            return;
        }
        Instant now = clock.instant();
        OrderStatus from = order.getStatus();
        order.confirm(now);
        historyRepository.save(new OrderStatusChange(order, from, order.getStatus(), "payment captured", now));
        outboxWriter.enqueue(SagaTopics.ORDER_CONFIRMED, order.getId(), event.eventId(),
                new OrderConfirmed(order.getId(), order.getUserId()));
    }

    @Transactional
    public void onPaymentFailed(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        PaymentFailed failed = sagaJson.readPayload(event, PaymentFailed.class);
        CustomerOrder order = require(failed.orderId());
        if (order.getStatus() == OrderStatus.FAILED || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            log.info("Order {} is {}; ignoring payment-failed", order.getId(), order.getStatus());
            return;
        }
        Instant now = clock.instant();
        OrderStatus from = order.getStatus();
        order.fail(now);
        historyRepository.save(new OrderStatusChange(order, from, order.getStatus(),
                "payment failed: " + failed.failureCode(), now));
        outboxWriter.enqueue(SagaTopics.ORDER_CANCELLED, order.getId(), event.eventId(),
                new OrderCancelled(order.getId(), order.getUserId(), PAYMENT_FAILED, false));
    }

    @Transactional
    public void onInventoryReleased(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        long orderId = Long.parseLong(event.correlationId());
        CustomerOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        log.info("Stock released for order {} in status {}", order.getId(), order.getStatus());
    }

    @Transactional
    public void onPaymentRefunded(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        PaymentRefunded refunded = sagaJson.readPayload(event, PaymentRefunded.class);
        CustomerOrder order = require(refunded.orderId());
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.info("Order {} is {}; payment refund is recorded by payment-service", order.getId(), order.getStatus());
            return;
        }
        Instant now = clock.instant();
        OrderStatus from = order.getStatus();
        order.refund(now);
        historyRepository.save(new OrderStatusChange(order, from, order.getStatus(), "payment refunded", now));
    }

    private CustomerOrder require(long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " does not exist"));
    }
}

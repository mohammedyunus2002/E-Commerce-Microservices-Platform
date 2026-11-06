package com.ecommerce.notification.saga;

import com.ecommerce.messaging.Idempotency;
import com.ecommerce.messaging.SagaEvent;
import com.ecommerce.messaging.SagaJson;
import com.ecommerce.messaging.SagaPayloads.OrderCancelled;
import com.ecommerce.messaging.SagaPayloads.OrderConfirmed;
import com.ecommerce.messaging.SagaPayloads.PaymentCompleted;
import com.ecommerce.messaging.SagaPayloads.PaymentFailed;
import com.ecommerce.messaging.SagaPayloads.PaymentRefunded;
import com.ecommerce.messaging.SagaTopics;
import com.ecommerce.notification.domain.NotificationType;
import com.ecommerce.notification.dto.RecordNotificationRequest;
import com.ecommerce.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true")
public class NotificationSaga {

    private static final Logger log = LoggerFactory.getLogger(NotificationSaga.class);

    private final NotificationService notificationService;
    private final Idempotency idempotency;
    private final SagaJson sagaJson;

    public NotificationSaga(NotificationService notificationService, Idempotency idempotency, SagaJson sagaJson) {
        this.notificationService = notificationService;
        this.idempotency = idempotency;
        this.sagaJson = sagaJson;
    }

    @Transactional
    public void onPaymentCompleted(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        PaymentCompleted completed = sagaJson.readPayload(event, PaymentCompleted.class);
        // The customer email is written from order-confirmed, after the order actually becomes CONFIRMED.
        log.info("Payment captured for order {}; confirmation email follows order-confirmed", completed.orderId());
    }

    @Transactional
    public void onOrderConfirmed(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        OrderConfirmed confirmed = sagaJson.readPayload(event, OrderConfirmed.class);
        notificationService.record(new RecordNotificationRequest(confirmed.userId(), confirmed.orderId(),
                NotificationType.ORDER_CONFIRMED, "Your order is confirmed",
                "Order " + confirmed.orderId() + " is confirmed."));
    }

    @Transactional
    public void onOrderCancelled(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        OrderCancelled cancelled = sagaJson.readPayload(event, OrderCancelled.class);
        if ("PAYMENT_FAILED".equals(cancelled.reason())) {
            return;
        }
        notificationService.record(new RecordNotificationRequest(cancelled.userId(), cancelled.orderId(),
                NotificationType.ORDER_CANCELLED, "Your order was cancelled",
                "Order " + cancelled.orderId() + " was cancelled."));
    }

    @Transactional
    public void onPaymentFailed(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        PaymentFailed failed = sagaJson.readPayload(event, PaymentFailed.class);
        notificationService.record(new RecordNotificationRequest(failed.userId(), failed.orderId(),
                NotificationType.PAYMENT_FAILED, "Payment failed",
                "Payment for order " + failed.orderId() + " was declined (" + failed.failureCode() + ")."));
    }

    @Transactional
    public void onPaymentRefunded(SagaEvent event) {
        if (!idempotency.firstTime(event.eventId(), event.type())) {
            return;
        }
        PaymentRefunded refunded = sagaJson.readPayload(event, PaymentRefunded.class);
        notificationService.record(new RecordNotificationRequest(refunded.userId(), refunded.orderId(),
                NotificationType.PAYMENT_REFUNDED, "Your payment was refunded",
                "The payment for order " + refunded.orderId() + " was refunded."));
    }
}

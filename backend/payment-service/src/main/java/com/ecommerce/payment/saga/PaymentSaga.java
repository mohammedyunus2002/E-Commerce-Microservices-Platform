package com.ecommerce.payment.saga;

import com.ecommerce.messaging.Idempotency;
import com.ecommerce.messaging.OutboxWriter;
import com.ecommerce.messaging.SagaEvent;
import com.ecommerce.messaging.SagaJson;
import com.ecommerce.messaging.SagaPayloads.OrderCancelled;
import com.ecommerce.messaging.SagaPayloads.PaymentCompleted;
import com.ecommerce.messaging.SagaPayloads.PaymentFailed;
import com.ecommerce.messaging.SagaPayloads.PaymentRefunded;
import com.ecommerce.messaging.SagaPayloads.PaymentRequested;
import com.ecommerce.messaging.SagaTopics;
import com.ecommerce.payment.domain.OrderTombstone;
import com.ecommerce.payment.domain.Payment;
import com.ecommerce.payment.domain.PaymentStatus;
import com.ecommerce.payment.repository.OrderTombstoneRepository;
import com.ecommerce.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Service
@ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true")
public class PaymentSaga {

    private static final Logger log = LoggerFactory.getLogger(PaymentSaga.class);

    private final PaymentService paymentService;
    private final OrderTombstoneRepository tombstones;
    private final OrderGateLock gateLock;
    private final Idempotency idempotency;
    private final OutboxWriter outboxWriter;
    private final SagaJson sagaJson;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PaymentSaga(PaymentService paymentService, OrderTombstoneRepository tombstones, OrderGateLock gateLock,
                       Idempotency idempotency, OutboxWriter outboxWriter, SagaJson sagaJson,
                       PlatformTransactionManager transactionManager, Clock clock) {
        this.paymentService = paymentService;
        this.tombstones = tombstones;
        this.gateLock = gateLock;
        this.idempotency = idempotency;
        this.outboxWriter = outboxWriter;
        this.sagaJson = sagaJson;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void onPaymentRequested(SagaEvent event) {
        PaymentRequested requested = sagaJson.readPayload(event, PaymentRequested.class);
        transactionTemplate.executeWithoutResult(status -> gateLock.lock(requested.orderId(), () -> {
            if (!idempotency.firstTime(event.eventId(), event.type())) {
                return;
            }
            if (tombstones.existsById(requested.orderId())) {
                log.info("Order {} was cancelled before it could be charged", requested.orderId());
                return;
            }
            Payment payment = paymentService.recordSagaCharge(requested.orderId(), requested.userId(),
                    requested.amount(), requested.currency());
            if (payment.getStatus() == PaymentStatus.CAPTURED) {
                outboxWriter.enqueue(SagaTopics.PAYMENT_COMPLETED, requested.orderId(), event.eventId(),
                        new PaymentCompleted(requested.orderId(), requested.userId(), payment.getAmount(),
                                payment.getCurrency()));
            } else if (payment.getStatus() == PaymentStatus.FAILED) {
                outboxWriter.enqueue(SagaTopics.PAYMENT_FAILED, requested.orderId(), event.eventId(),
                        new PaymentFailed(requested.orderId(), requested.userId(), payment.getFailureCode()));
            }
        }));
    }

    public void onOrderCancelled(SagaEvent event) {
        OrderCancelled cancelled = sagaJson.readPayload(event, OrderCancelled.class);
        transactionTemplate.executeWithoutResult(status -> gateLock.lock(cancelled.orderId(), () -> {
            if (!idempotency.firstTime(event.eventId(), event.type())) {
                return;
            }
            if (!tombstones.existsById(cancelled.orderId())) {
                tombstones.save(new OrderTombstone(cancelled.orderId(), clock.instant()));
            }
            Payment payment = paymentService.refundCaptured(cancelled.orderId());
            if (payment != null && payment.getStatus() == PaymentStatus.REFUNDED) {
                outboxWriter.enqueue(SagaTopics.PAYMENT_REFUNDED, cancelled.orderId(), event.eventId(),
                        new PaymentRefunded(cancelled.orderId(), cancelled.userId()));
            }
        }));
    }
}

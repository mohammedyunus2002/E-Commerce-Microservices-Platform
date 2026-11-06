package com.ecommerce.inventory.saga;

import com.ecommerce.inventory.domain.OrderTombstone;
import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.dto.ReservationLine;
import com.ecommerce.inventory.repository.OrderTombstoneRepository;
import com.ecommerce.inventory.service.InsufficientStockException;
import com.ecommerce.inventory.service.ReservationService;
import com.ecommerce.messaging.Idempotency;
import com.ecommerce.messaging.OutboxWriter;
import com.ecommerce.messaging.SagaEvent;
import com.ecommerce.messaging.SagaJson;
import com.ecommerce.messaging.SagaPayloads.InventoryRejected;
import com.ecommerce.messaging.SagaPayloads.InventoryReleased;
import com.ecommerce.messaging.SagaPayloads.InventoryReserved;
import com.ecommerce.messaging.SagaPayloads.OrderCancelled;
import com.ecommerce.messaging.SagaPayloads.OrderConfirmed;
import com.ecommerce.messaging.SagaPayloads.OrderCreated;
import com.ecommerce.messaging.SagaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true")
public class InventorySaga {

    private static final Logger log = LoggerFactory.getLogger(InventorySaga.class);

    private final ReservationService reservationService;
    private final OrderTombstoneRepository tombstones;
    private final OrderGateLock gateLock;
    private final Idempotency idempotency;
    private final OutboxWriter outboxWriter;
    private final SagaJson sagaJson;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public InventorySaga(ReservationService reservationService, OrderTombstoneRepository tombstones,
                         OrderGateLock gateLock, Idempotency idempotency, OutboxWriter outboxWriter,
                         SagaJson sagaJson, PlatformTransactionManager transactionManager, Clock clock) {
        this.reservationService = reservationService;
        this.tombstones = tombstones;
        this.gateLock = gateLock;
        this.idempotency = idempotency;
        this.outboxWriter = outboxWriter;
        this.sagaJson = sagaJson;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void onOrderCreated(SagaEvent event) {
        OrderCreated created = sagaJson.readPayload(event, OrderCreated.class);
        try {
            transactionTemplate.executeWithoutResult(status -> gateLock.lock(created.orderId(), () -> {
                if (!idempotency.firstTime(event.eventId(), event.type())) {
                    return;
                }
                if (tombstones.existsById(created.orderId())) {
                    log.info("Order {} was cancelled before stock could be reserved", created.orderId());
                    return;
                }
                List<ReservationLine> lines = created.lines().stream()
                        .map(line -> new ReservationLine(line.productId(), line.quantity()))
                        .toList();
                reservationService.reserve(created.orderId(), lines);
                outboxWriter.enqueue(SagaTopics.INVENTORY_RESERVED, created.orderId(), event.eventId(),
                        new InventoryReserved(created.orderId()));
            }));
        } catch (InsufficientStockException ex) {
            transactionTemplate.executeWithoutResult(status -> gateLock.lock(created.orderId(), () -> {
                if (!idempotency.firstTime(event.eventId(), event.type())) {
                    return;
                }
                outboxWriter.enqueue(SagaTopics.INVENTORY_REJECTED, created.orderId(), event.eventId(),
                        new InventoryRejected(created.orderId(), ex.getMessage()));
            }));
        }
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
            boolean held = reservationService.findByOrder(cancelled.orderId()).stream()
                    .anyMatch(line -> line.status() == ReservationStatus.RESERVED
                            || line.status() == ReservationStatus.COMMITTED);
            if (!held) {
                log.info("Order {} has no stock to release", cancelled.orderId());
                return;
            }
            reservationService.release(cancelled.orderId());
            reservationService.restockCommitted(cancelled.orderId());
            outboxWriter.enqueue(SagaTopics.INVENTORY_RELEASED, cancelled.orderId(), event.eventId(),
                    new InventoryReleased(cancelled.orderId()));
        }));
    }

    public void onOrderConfirmed(SagaEvent event) {
        OrderConfirmed confirmed = sagaJson.readPayload(event, OrderConfirmed.class);
        transactionTemplate.executeWithoutResult(status -> gateLock.lock(confirmed.orderId(), () -> {
            if (!idempotency.firstTime(event.eventId(), event.type())) {
                return;
            }
            if (tombstones.existsById(confirmed.orderId())) {
                log.info("Order {} is cancelled; leaving stock uncommitted", confirmed.orderId());
                return;
            }
            reservationService.commit(confirmed.orderId());
        }));
    }
}

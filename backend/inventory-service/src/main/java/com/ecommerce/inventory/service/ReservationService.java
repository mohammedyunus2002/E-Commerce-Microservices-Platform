package com.ecommerce.inventory.service;

import com.ecommerce.inventory.domain.InventoryItem;
import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.StockReservation;
import com.ecommerce.inventory.dto.ReservationLine;
import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Holds, releases, and commits stock for orders. All three operations are idempotent per order,
 * because the saga delivers messages at least once.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final InventoryItemRepository inventoryItemRepository;
    private final StockReservationRepository reservationRepository;
    private final Clock clock;

    public ReservationService(InventoryItemRepository inventoryItemRepository,
                              StockReservationRepository reservationRepository,
                              Clock clock) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    /**
     * Reserves every line or none of them. If the order already has reservations, they are
     * returned unchanged instead of reserving twice.
     *
     * @throws InsufficientStockException if any product lacks stock; nothing is reserved
     */
    @Transactional
    public List<ReservationResponse> reserve(long orderId, List<ReservationLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An order must reserve at least one line");
        }
        List<StockReservation> existing = reservationRepository.findByOrderIdOrderByProductIdAsc(orderId);
        if (!existing.isEmpty()) {
            log.info("Order {} already has {} reservation lines; not reserving again", orderId, existing.size());
            return existing.stream().map(ReservationResponse::from).toList();
        }

        // Locks are taken in ascending product order so two orders for the same products cannot deadlock.
        SortedMap<Long, Integer> quantities = mergeByProduct(lines);
        Instant now = clock.instant();
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            long productId = line.getKey();
            int quantity = line.getValue();
            if (inventoryItemRepository.reserve(productId, quantity, now) == 0) {
                int available = inventoryItemRepository.findById(productId)
                        .map(InventoryItem::getAvailableQuantity)
                        .orElse(0);
                log.info("Order {} rejected: product {} has {} available, {} requested",
                        orderId, productId, available, quantity);
                throw new InsufficientStockException(productId, quantity, available);
            }
        }

        List<StockReservation> reservations = new ArrayList<>(quantities.size());
        quantities.forEach((productId, quantity) ->
                reservations.add(new StockReservation(orderId, productId, quantity, now)));
        // A concurrent duplicate for the same order fails here on the unique key and rolls back its decrements.
        List<StockReservation> saved = reservationRepository.saveAllAndFlush(reservations);
        log.info("Order {} reserved {} product lines", orderId, saved.size());
        return saved.stream().map(ReservationResponse::from).toList();
    }

    /** Returns held units to available stock. Lines already released or committed are left alone. */
    @Transactional
    public List<ReservationResponse> release(long orderId) {
        return settle(orderId, ReservationStatus.RELEASED);
    }

    /** Consumes held units permanently once the order is confirmed. */
    @Transactional
    public List<ReservationResponse> commit(long orderId) {
        return settle(orderId, ReservationStatus.COMMITTED);
    }

    /**
     * Returns committed units to available stock. Used when a confirmed order is cancelled.
     * {@link #release(long)} does not do this: a release after commit must not restock by accident.
     */
    @Transactional
    public int restockCommitted(long orderId) {
        List<StockReservation> reservations = reservationRepository.findByOrderIdOrderByProductIdAsc(orderId);
        Instant now = clock.instant();
        int changed = 0;
        for (StockReservation reservation : reservations) {
            if (reservationRepository.transition(reservation.getId(), ReservationStatus.COMMITTED,
                    ReservationStatus.RELEASED, now) == 0) {
                continue;
            }
            if (inventoryItemRepository.restock(reservation.getProductId(), reservation.getQuantity(), now) == 0) {
                throw new IllegalStateException("Product " + reservation.getProductId()
                        + " disappeared while restocking reservation " + reservation.getId());
            }
            changed++;
        }
        log.info("Order {}: restocked {} committed lines", orderId, changed);
        return changed;
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> findByOrder(long orderId) {
        return reservationRepository.findByOrderIdOrderByProductIdAsc(orderId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    private List<ReservationResponse> settle(long orderId, ReservationStatus target) {
        List<StockReservation> reservations = reservationRepository.findByOrderIdOrderByProductIdAsc(orderId);
        Instant now = clock.instant();
        int changed = 0;
        for (StockReservation reservation : reservations) {
            if (reservationRepository.transition(reservation.getId(), ReservationStatus.RESERVED, target, now) == 0) {
                continue;
            }
            int updated = target == ReservationStatus.RELEASED
                    ? inventoryItemRepository.release(reservation.getProductId(), reservation.getQuantity(), now)
                    : inventoryItemRepository.commitReserved(reservation.getProductId(), reservation.getQuantity(), now);
            if (updated == 0) {
                // Reserved stock lower than an active reservation means the data is corrupt; roll back loudly.
                throw new IllegalStateException("Reserved quantity of product " + reservation.getProductId()
                        + " is lower than reservation " + reservation.getId());
            }
            changed++;
        }
        log.info("Order {}: {} of {} reservation lines moved to {}", orderId, changed, reservations.size(), target);
        return findByOrder(orderId);
    }

    private static SortedMap<Long, Integer> mergeByProduct(List<ReservationLine> lines) {
        SortedMap<Long, Integer> quantities = new TreeMap<>();
        for (ReservationLine line : lines) {
            if (line.productId() == null || line.quantity() <= 0) {
                throw new IllegalArgumentException("Reservation lines need a product ID and a positive quantity");
            }
            quantities.merge(line.productId(), line.quantity(), Math::addExact);
        }
        return quantities;
    }
}

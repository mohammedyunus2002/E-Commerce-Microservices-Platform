package com.ecommerce.inventory.service;

import com.ecommerce.inventory.domain.InventoryItem;
import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.dto.ReservationLine;
import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceIntegrationTest {

    private static final long PHONE = 101L;
    private static final long CASE = 102L;

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private InventoryItemRepository itemRepository;
    @Autowired
    private StockReservationRepository reservationRepository;

    @BeforeEach
    void seedStock() {
        reservationRepository.deleteAll();
        itemRepository.deleteAll();
        itemRepository.saveAndFlush(InventoryItem.create(PHONE, 5, Instant.now()));
        itemRepository.saveAndFlush(InventoryItem.create(CASE, 2, Instant.now()));
    }

    @Test
    void reserveMovesUnitsFromAvailableToReservedAndMergesDuplicateLines() {
        List<ReservationResponse> result = reservationService.reserve(1L, List.of(
                new ReservationLine(PHONE, 2), new ReservationLine(CASE, 1), new ReservationLine(PHONE, 1)));

        assertThat(result).extracting(ReservationResponse::productId, ReservationResponse::quantity)
                .containsExactly(tuple(PHONE, 3), tuple(CASE, 1));
        assertThat(result).allMatch(r -> r.status() == ReservationStatus.RESERVED);
        assertStock(PHONE, 2, 3);
        assertStock(CASE, 1, 1);
    }

    @Test
    void reserveIsAllOrNothingWhenOneLineLacksStock() {
        assertThatThrownBy(() -> reservationService.reserve(2L, List.of(
                new ReservationLine(PHONE, 1), new ReservationLine(CASE, 3))))
                .isInstanceOfSatisfying(InsufficientStockException.class, e -> {
                    assertThat(e.getProductId()).isEqualTo(CASE);
                    assertThat(e.getRequested()).isEqualTo(3);
                    assertThat(e.getAvailable()).isEqualTo(2);
                });

        // PHONE was decremented first in the same transaction; the rollback must have undone it.
        assertStock(PHONE, 5, 0);
        assertStock(CASE, 2, 0);
        assertThat(reservationRepository.findByOrderIdOrderByProductIdAsc(2L)).isEmpty();
    }

    @Test
    void reserveRejectsProductWithoutStockRecord() {
        assertThatThrownBy(() -> reservationService.reserve(3L, List.of(new ReservationLine(999L, 1))))
                .isInstanceOfSatisfying(InsufficientStockException.class,
                        e -> assertThat(e.getAvailable()).isZero());
    }

    @Test
    void reservingTheSameOrderTwiceReturnsTheOriginalReservation() {
        List<ReservationResponse> first = reservationService.reserve(4L, List.of(new ReservationLine(PHONE, 2)));
        List<ReservationResponse> second = reservationService.reserve(4L, List.of(new ReservationLine(PHONE, 2)));

        assertThat(second).extracting(ReservationResponse::id)
                .containsExactlyElementsOf(first.stream().map(ReservationResponse::id).toList());
        assertStock(PHONE, 3, 2);
    }

    @Test
    void releaseReturnsUnitsOnceEvenWhenRepeated() {
        reservationService.reserve(5L, List.of(new ReservationLine(PHONE, 2), new ReservationLine(CASE, 2)));

        List<ReservationResponse> released = reservationService.release(5L);
        reservationService.release(5L);

        assertThat(released).allMatch(r -> r.status() == ReservationStatus.RELEASED);
        assertStock(PHONE, 5, 0);
        assertStock(CASE, 2, 0);
    }

    @Test
    void commitConsumesReservedUnitsAndLaterReleaseChangesNothing() {
        reservationService.reserve(6L, List.of(new ReservationLine(PHONE, 2)));

        List<ReservationResponse> committed = reservationService.commit(6L);
        reservationService.release(6L);

        assertThat(committed).singleElement().extracting(ReservationResponse::status)
                .isEqualTo(ReservationStatus.COMMITTED);
        assertStock(PHONE, 3, 0);
    }

    @Test
    void restockCommittedReturnsUnitsThatWereAlreadySold() {
        reservationService.reserve(7L, List.of(new ReservationLine(PHONE, 2)));
        reservationService.commit(7L);

        assertThat(reservationService.restockCommitted(7L)).isEqualTo(1);
        reservationService.restockCommitted(7L);

        assertStock(PHONE, 5, 0);
        assertThat(reservationRepository.findByOrderIdOrderByProductIdAsc(7L))
                .allMatch(reservation -> reservation.getStatus() == ReservationStatus.RELEASED);
    }

    @Test
    void releasingAnOrderWithoutReservationsIsANoOp() {
        assertThat(reservationService.release(404L)).isEmpty();
        assertStock(PHONE, 5, 0);
    }

    private void assertStock(long productId, int available, int reserved) {
        InventoryItem item = itemRepository.findById(productId).orElseThrow();
        assertThat(item.getAvailableQuantity()).as("available of %d", productId).isEqualTo(available);
        assertThat(item.getReservedQuantity()).as("reserved of %d", productId).isEqualTo(reserved);
    }
}

package com.ecommerce.inventory.service;

import com.ecommerce.inventory.domain.InventoryItem;
import com.ecommerce.inventory.dto.ReservationLine;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real concurrency against MySQL row locks; H2 cannot show these behaviours faithfully.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ReservationConcurrencyMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private InventoryItemRepository itemRepository;
    @Autowired
    private StockReservationRepository reservationRepository;

    private final ExecutorService pool = Executors.newFixedThreadPool(16);

    @BeforeEach
    void clean() {
        reservationRepository.deleteAll();
        itemRepository.deleteAll();
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        itemRepository.saveAndFlush(InventoryItem.create(1L, 10, Instant.now()));

        List<Callable<Boolean>> orders = new ArrayList<>();
        for (long orderId = 1; orderId <= 40; orderId++) {
            long id = orderId;
            orders.add(() -> {
                try {
                    reservationService.reserve(id, List.of(new ReservationLine(1L, 1)));
                    return true;
                } catch (InsufficientStockException e) {
                    return false;
                }
            });
        }

        long succeeded = runTogether(orders).stream().filter(Boolean::booleanValue).count();

        assertThat(succeeded).isEqualTo(10);
        InventoryItem item = itemRepository.findById(1L).orElseThrow();
        assertThat(item.getAvailableQuantity()).isZero();
        assertThat(item.getReservedQuantity()).isEqualTo(10);
        assertThat(reservationRepository.count()).isEqualTo(10);
    }

    @Test
    void ordersListingProductsInOppositeOrderDoNotDeadlock() throws Exception {
        itemRepository.saveAndFlush(InventoryItem.create(2L, 100, Instant.now()));
        itemRepository.saveAndFlush(InventoryItem.create(3L, 100, Instant.now()));

        List<Callable<Boolean>> orders = new ArrayList<>();
        for (long orderId = 100; orderId < 130; orderId++) {
            long id = orderId;
            List<ReservationLine> lines = orderId % 2 == 0
                    ? List.of(new ReservationLine(2L, 1), new ReservationLine(3L, 1))
                    : List.of(new ReservationLine(3L, 1), new ReservationLine(2L, 1));
            orders.add(() -> {
                reservationService.reserve(id, lines);
                return true;
            });
        }

        // Any deadlock would surface here as an ExecutionException wrapping CannotAcquireLockException.
        assertThat(runTogether(orders)).hasSize(30).containsOnly(true);
        assertThat(itemRepository.findById(2L).orElseThrow().getAvailableQuantity()).isEqualTo(70);
        assertThat(itemRepository.findById(3L).orElseThrow().getAvailableQuantity()).isEqualTo(70);
    }

    @Test
    void duplicateDeliveriesOfTheSameOrderReserveOnce() throws Exception {
        itemRepository.saveAndFlush(InventoryItem.create(4L, 50, Instant.now()));

        List<Callable<Boolean>> deliveries = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            deliveries.add(() -> {
                try {
                    reservationService.reserve(777L, List.of(new ReservationLine(4L, 5)));
                    return true;
                } catch (DataIntegrityViolationException e) {
                    // Lost the race on the (order_id, product_id) unique key; its decrement rolled back.
                    return false;
                }
            });
        }

        assertThat(runTogether(deliveries)).contains(true);
        InventoryItem item = itemRepository.findById(4L).orElseThrow();
        assertThat(item.getAvailableQuantity()).isEqualTo(45);
        assertThat(item.getReservedQuantity()).isEqualTo(5);
        assertThat(reservationRepository.findByOrderIdOrderByProductIdAsc(777L)).hasSize(1);
    }

    private List<Boolean> runTogether(List<Callable<Boolean>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        return results;
    }
}

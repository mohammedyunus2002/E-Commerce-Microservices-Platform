package com.ecommerce.order.repository;

import com.ecommerce.order.domain.CustomerOrder;
import com.ecommerce.order.domain.OrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applies the Flyway migration to MySQL 8.4, including the CHECK constraints H2 does not enforce
 * the same way.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OrderMySqlMigrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Test
    void migrationStoresAnOrderAndRejectsANonPositiveTotal() {
        Instant now = Instant.parse("2026-11-06T10:00:00Z");
        CustomerOrder order = new CustomerOrder(8L, new BigDecimal("10.00"), now);
        order.addItem(new OrderItem(1L, "HEAD-100", "Headphones", new BigDecimal("10.00"), 1,
                new BigDecimal("10.00")));
        CustomerOrder saved = orderRepository.saveAndFlush(order);

        CustomerOrder loaded = orderRepository.findWithItemsById(saved.getId()).orElseThrow();
        assertThat(loaded.getItems()).hasSize(1);
        assertThat(loaded.getItems().get(0).getSku()).isEqualTo("HEAD-100");

        // MySQL reports CHECK failures as error 3819, which Hibernate does not classify as a
        // constraint violation, so Spring surfaces a generic DataAccessException here.
        assertThatThrownBy(() -> orderRepository.saveAndFlush(new CustomerOrder(8L, BigDecimal.ZERO, now)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_customer_orders_total_positive");
    }
}

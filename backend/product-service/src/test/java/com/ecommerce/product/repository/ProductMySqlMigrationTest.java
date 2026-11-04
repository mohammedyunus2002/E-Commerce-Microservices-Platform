package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applies the Flyway migrations to MySQL 8.4 and runs search queries against them, including
 * the CHECK constraint that MySQL enforces since 8.0.16.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProductMySqlMigrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void searchRunsOnMySqlAndCheckConstraintRejectsNonPositivePrice() {
        Category category = categoryRepository.saveAndFlush(new Category("Audio", "audio", null));
        productRepository.saveAndFlush(new Product("HEAD-1", "Headphones", "Noise cancelling",
                new BigDecimal("199.00"), null, category));

        var page = productRepository.findAll(
                Specification.allOf(ProductSpecifications.activeOnly(true),
                        ProductSpecifications.textMatches("NOISE")),
                PageRequest.of(0, 10, Sort.by("price")));
        assertThat(page.getContent()).extracting(Product::getSku).containsExactly("HEAD-1");
        assertThat(page.getContent().get(0).getCategory().getName()).isEqualTo("Audio");

        // MySQL reports CHECK failures as error 3819, which Hibernate does not classify as a
        // constraint violation, so Spring surfaces a generic DataAccessException here.
        assertThatThrownBy(() -> productRepository.saveAndFlush(new Product("FREE-1", "Free", null,
                BigDecimal.ZERO, null, category)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_products_price_positive");
    }
}

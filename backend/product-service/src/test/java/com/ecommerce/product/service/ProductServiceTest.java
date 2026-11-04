package com.ecommerce.product.service;

import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.dto.ProductSearchCriteria;
import com.ecommerce.product.dto.UpdateProductRequest;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private ProductService productService;
    private Category electronics;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository);
        electronics = new Category("Electronics", "electronics", null);
        ReflectionTestUtils.setField(electronics, "id", 1L);
    }

    @Test
    void createPersistsProductInCategory() {
        when(productRepository.existsBySku("PHONE-1")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        ProductResponse response = productService.create(new CreateProductRequest(
                "PHONE-1", " Pixel ", "  ", new BigDecimal("499.00"), null, 1L));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Pixel");
        assertThat(response.description()).isNull();
        assertThat(response.active()).isTrue();
        assertThat(response.category().slug()).isEqualTo("electronics");
    }

    @Test
    void createRejectsDuplicateSku() {
        when(productRepository.existsBySku("PHONE-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(new CreateProductRequest(
                "PHONE-1", "Pixel", null, BigDecimal.TEN, null, 1L)))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SKU_ALREADY_EXISTS");
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void createMapsSkuRaceToConflict() {
        when(productRepository.existsBySku("PHONE-1")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(productRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_products_sku"));

        assertThatThrownBy(() -> productService.create(new CreateProductRequest(
                "PHONE-1", "Pixel", null, BigDecimal.TEN, null, 1L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRejectsUnknownCategory() {
        when(productRepository.existsBySku("PHONE-1")).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(new CreateProductRequest(
                "PHONE-1", "Pixel", null, BigDecimal.TEN, null, 99L)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("CATEGORY_NOT_FOUND");
    }

    @Test
    void updateChangesFieldsAndCanDeactivate() {
        Product product = new Product("PHONE-1", "Pixel", null, BigDecimal.TEN, null, electronics);
        ReflectionTestUtils.setField(product, "id", 10L);
        when(productRepository.findWithCategoryById(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));

        ProductResponse response = productService.update(10L, new UpdateProductRequest(
                "Pixel 9", "New model", new BigDecimal("799.99"), "https://img.example/p.png", 1L, false));

        assertThat(response.name()).isEqualTo("Pixel 9");
        assertThat(response.price()).isEqualByComparingTo("799.99");
        assertThat(response.active()).isFalse();
    }

    @Test
    void getMissingProductIsNotFound() {
        when(productRepository.findWithCategoryById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchRejectsInvertedPriceRange() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                null, null, new BigDecimal("100"), new BigDecimal("10"), true);

        assertThatThrownBy(() -> productService.search(criteria, PageRequest.of(0, 10)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("INVALID_PRICE_RANGE");
    }
}

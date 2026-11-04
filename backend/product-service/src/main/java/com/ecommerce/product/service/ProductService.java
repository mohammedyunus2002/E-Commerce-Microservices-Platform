package com.ecommerce.product.service;

import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.product.cache.CacheNames;
import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.dto.ProductSearchCriteria;
import com.ecommerce.product.dto.UpdateProductRequest;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ecommerce.product.repository.ProductSpecifications.activeOnly;
import static com.ecommerce.product.repository.ProductSpecifications.inCategory;
import static com.ecommerce.product.repository.ProductSpecifications.priceAtLeast;
import static com.ecommerce.product.repository.ProductSpecifications.priceAtMost;
import static com.ecommerce.product.repository.ProductSpecifications.textMatches;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Cached regardless of status; callers decide whether inactive products are visible.
     */
    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getProduct(long id) {
        return productRepository.findWithCategoryById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE_RANGE",
                    "minPrice must not be greater than maxPrice");
        }
        Specification<Product> spec = Specification.allOf(
                activeOnly(criteria.activeOnly()),
                textMatches(criteria.query()),
                inCategory(criteria.categoryId()),
                priceAtLeast(criteria.minPrice()),
                priceAtMost(criteria.maxPrice()));
        return PageResponse.from(productRepository.findAll(spec, pageable), ProductResponse::from);
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw duplicateSku(request.sku());
        }
        Product product = new Product(
                request.sku(),
                request.name().trim(),
                trimToNull(request.description()),
                request.price(),
                trimToNull(request.imageUrl()),
                requireCategory(request.categoryId()));
        try {
            product = productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateSku(request.sku());
        }
        log.info("Created product {} ({})", product.getId(), product.getSku());
        return ProductResponse.from(product);
    }

    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    @Transactional
    public ProductResponse update(long id, UpdateProductRequest request) {
        Product product = find(id);
        product.update(
                request.name().trim(),
                trimToNull(request.description()),
                request.price(),
                trimToNull(request.imageUrl()),
                requireCategory(request.categoryId()),
                request.active());
        productRepository.flush();
        log.info("Updated product {}", id);
        return ProductResponse.from(product);
    }

    /**
     * Soft delete. Orders keep referencing the product ID, so the row is retained and only
     * hidden from shoppers. An update with {@code active=true} restores it.
     */
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    @Transactional
    public void deactivate(long id) {
        find(id).deactivate();
        log.info("Deactivated product {}", id);
    }

    private Product find(long id) {
        return productRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category requireCategory(long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST, "CATEGORY_NOT_FOUND", "Category " + categoryId + " does not exist"));
    }

    private static ConflictException duplicateSku(String sku) {
        return new ConflictException("SKU_ALREADY_EXISTS", "A product with SKU " + sku + " already exists");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

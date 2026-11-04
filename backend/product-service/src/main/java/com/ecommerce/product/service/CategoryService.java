package com.ecommerce.product.service;

import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.product.cache.CacheNames;
import com.ecommerce.product.domain.Category;
import com.ecommerce.product.dto.CategoryRequest;
import com.ecommerce.product.dto.CategoryResponse;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Cacheable(cacheNames = CacheNames.CATEGORIES, key = "'all'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        String slug = Slugs.of(name);
        if (categoryRepository.existsByNameIgnoreCase(name) || categoryRepository.existsBySlug(slug)) {
            throw duplicateName(name);
        }
        Category category = saveOrConflict(new Category(name, slug, trimToNull(request.description())), name);
        log.info("Created category {} '{}'", category.getId(), name);
        return CategoryResponse.from(category);
    }

    // Product responses embed the category name and slug, so cached products are cleared too.
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCTS, allEntries = true)
    })
    @Transactional
    public CategoryResponse update(long id, CategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();
        String slug = Slugs.of(name);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)
                || categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw duplicateName(name);
        }
        category.rename(name, slug, trimToNull(request.description()));
        saveOrConflict(category, name);
        log.info("Updated category {}", id);
        return CategoryResponse.from(category);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCTS, allEntries = true)
    })
    @Transactional
    public void delete(long id) {
        Category category = find(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("CATEGORY_IN_USE",
                    "Category " + id + " still has products. Move or deactivate them first.");
        }
        categoryRepository.delete(category);
        log.info("Deleted category {}", id);
    }

    Category find(long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private Category saveOrConflict(Category category, String name) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateName(name);
        }
    }

    private static ConflictException duplicateName(String name) {
        return new ConflictException("CATEGORY_ALREADY_EXISTS", "A category named '" + name + "' already exists");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

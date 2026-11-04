package com.ecommerce.product.web;

import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.product.dto.CategoryResponse;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.dto.ProductSearchCriteria;
import com.ecommerce.product.service.CategoryService;
import com.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public, shopper-facing catalog reads. Only active products are visible here.
 */
@RestController
@Validated
@Tag(name = "Catalog")
@SecurityRequirements
public class CatalogController {

    private final ProductService productService;
    private final CategoryService categoryService;

    public CatalogController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping("/api/products")
    @Operation(summary = "Search active products with filters, paging, and sorting",
            description = "sort accepts name, price, createdAt, or newest, optionally followed by ,asc or ,desc")
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort) {
        return productService.search(
                new ProductSearchCriteria(q, categoryId, minPrice, maxPrice, true),
                PageRequest.of(page, size, ProductSort.parse(sort)));
    }

    @GetMapping("/api/products/{id}")
    @Operation(summary = "Get an active product")
    public ProductResponse get(@PathVariable long id) {
        ProductResponse product = productService.getProduct(id);
        if (!product.active()) {
            throw new ResourceNotFoundException("Product", id);
        }
        return product;
    }

    @GetMapping("/api/categories")
    @Operation(summary = "List all categories")
    public List<CategoryResponse> categories() {
        return categoryService.listCategories();
    }
}

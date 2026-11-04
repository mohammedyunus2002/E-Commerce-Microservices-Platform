package com.ecommerce.product.web;

import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private long electronicsId;
    private long booksId;

    @BeforeEach
    void seedCatalog() throws Exception {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        electronicsId = createCategory("Electronics");
        booksId = createCategory("Books");
        createProduct("PHONE-1", "Pixel Phone", "Android phone", "499.00", electronicsId);
        createProduct("PHONE-2", "Budget Phone", "Cheap android", "149.99", electronicsId);
        createProduct("LAPTOP-1", "Ultrabook", "Thin laptop", "1299.00", electronicsId);
        createProduct("BOOK-1", "Java Concurrency", "A classic book", "45.50", booksId);
    }

    @Test
    void publicSearchFiltersByTextCategoryAndPriceAndSorts() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("q", "phone")
                        .param("categoryId", String.valueOf(electronicsId))
                        .param("maxPrice", "500")
                        .param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].sku", contains("PHONE-2", "PHONE-1")))
                .andExpect(jsonPath("$.content[0].category.name").value("Electronics"));
    }

    @Test
    void publicSearchPaginates() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "3").param("page", "1").param("sort", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[*].name", contains("Ultrabook")));
    }

    @Test
    void searchTreatsWildcardCharactersLiterally() throws Exception {
        mockMvc.perform(get("/api/products").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidSortAndPagingAreRejected() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
        mockMvc.perform(get("/api/products").param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deactivatedProductDisappearsFromPublicCatalogButNotAdmin() throws Exception {
        long id = productRepository.findAll().stream()
                .filter(p -> p.getSku().equals("BOOK-1")).findFirst().orElseThrow().getId();

        mockMvc.perform(delete("/api/admin/products/{id}", id).with(admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products").param("categoryId", String.valueOf(booksId)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/admin/products/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/admin/products").param("categoryId", String.valueOf(booksId)).with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adminUpdatesProduct() throws Exception {
        long id = productRepository.findAll().get(0).getId();

        mockMvc.perform(put("/api/admin/products/{id}", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Renamed",
                                "price", "10.00",
                                "categoryId", booksId,
                                "active", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.category.slug").value("books"));
    }

    @Test
    void createReturnsLocationAndRejectsDuplicateSku() throws Exception {
        mockMvc.perform(post("/api/admin/products").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("TABLET-1", "Tablet", "Big screen", "299.00", electronicsId))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/admin/products/"
                        + (productRepository.findAll().stream().mapToLong(p -> p.getId()).max().orElseThrow()))));

        mockMvc.perform(post("/api/admin/products").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("PHONE-1", "Copy", null, "1.00", electronicsId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_ALREADY_EXISTS"));
    }

    @Test
    void createValidatesInput() throws Exception {
        mockMvc.perform(post("/api/admin/products").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("bad sku", "", null, "-5", electronicsId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[?(@.field == 'sku')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field == 'price')]").exists());
    }

    @Test
    void customersAndAnonymousUsersCannotManageCatalog() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("X-100", "X", null, "1.00", electronicsId))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/products").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("X-100", "X", null, "1.00", electronicsId))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/categories/{id}", booksId).with(customer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void categoriesAreListedAlphabeticallyAndInUseCategoryCannotBeDeleted() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", contains("books", "electronics")));

        mockMvc.perform(delete("/api/admin/categories/{id}", booksId).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));

        mockMvc.perform(post("/api/admin/categories").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "ELECTRONICS"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"));
    }

    @Test
    void emptyCategoryCanBeRenamedAndDeleted() throws Exception {
        long toysId = createCategory("Toys");

        mockMvc.perform(put("/api/admin/categories/{id}", toysId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Toys & Games"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("toys-games"));

        mockMvc.perform(delete("/api/admin/categories/{id}", toysId).with(admin()))
                .andExpect(status().isNoContent());
    }

    private long createCategory(String name) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("id").asLong();
    }

    private void createProduct(String sku, String name, String description, String price, long categoryId)
            throws Exception {
        mockMvc.perform(post("/api/admin/products").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody(sku, name, description, price, categoryId))))
                .andExpect(status().isCreated());
    }

    private static Map<String, Object> productBody(String sku, String name, String description, String price,
                                                   long categoryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku", sku);
        body.put("name", name);
        body.put("description", description);
        body.put("price", price);
        body.put("categoryId", categoryId);
        return body;
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(j -> j.subject("2")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}

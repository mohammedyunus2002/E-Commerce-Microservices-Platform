package com.ecommerce.inventory.web;

import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.inventory.client.ProductCatalogClient;
import com.ecommerce.inventory.domain.InventoryItem;
import com.ecommerce.inventory.dto.ReservationLine;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import com.ecommerce.inventory.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryItemRepository itemRepository;
    @Autowired
    private StockReservationRepository reservationRepository;
    @Autowired
    private ReservationService reservationService;

    @MockitoBean
    private ProductCatalogClient productCatalogClient;

    @BeforeEach
    void seedStock() {
        reservationRepository.deleteAll();
        itemRepository.deleteAll();
        itemRepository.saveAndFlush(InventoryItem.create(1L, 8, Instant.now()));
        itemRepository.saveAndFlush(InventoryItem.create(2L, 0, Instant.now()));
        itemRepository.saveAndFlush(InventoryItem.create(3L, 3, Instant.now()));
    }

    @Test
    void availabilityIsPublicAndReportsUnknownProductsAsOutOfStock() throws Exception {
        mockMvc.perform(get("/api/inventory/availability/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(8))
                .andExpect(jsonPath("$.inStock").value(true));

        mockMvc.perform(get("/api/inventory/availability").param("productIds", "2", "99", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId", contains(2, 99, 1)))
                .andExpect(jsonPath("$[*].inStock", contains(false, false, true)));
    }

    @Test
    void availabilityListNeedsAtLeastOneId() throws Exception {
        mockMvc.perform(get("/api/inventory/availability"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/inventory"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/inventory").with(customer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminListsLowStockFirstAndFiltersByThreshold() throws Exception {
        mockMvc.perform(get("/api/admin/inventory").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].productId", contains(2, 3, 1)));

        mockMvc.perform(get("/api/admin/inventory").param("lowStockThreshold", "3").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].productId", contains(2, 3)));
    }

    @Test
    void settingStockForANewProductChecksTheCatalogWithTheCallersToken() throws Exception {
        mockMvc.perform(put("/api/admin/inventory/50").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availableQuantity\": 12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(50))
                .andExpect(jsonPath("$.availableQuantity").value(12))
                .andExpect(jsonPath("$.reservedQuantity").value(0));

        verify(productCatalogClient).requireProduct(50L, "admin-token");
        assertThat(itemRepository.findById(50L)).isPresent();
    }

    @Test
    void settingStockForAnExistingRecordSkipsTheCatalogCall() throws Exception {
        mockMvc.perform(put("/api/admin/inventory/1").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availableQuantity\": 20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(20));

        verify(productCatalogClient, never()).requireProduct(anyLong(), anyString());
    }

    @Test
    void settingStockForAProductMissingFromTheCatalogCreatesNothing() throws Exception {
        doThrow(new ResourceNotFoundException("Product", 60L))
                .when(productCatalogClient).requireProduct(60L, "admin-token");

        mockMvc.perform(put("/api/admin/inventory/60").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availableQuantity\": 5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(itemRepository.findById(60L)).isEmpty();
    }

    @Test
    void catalogOutageReturnsServiceUnavailable() throws Exception {
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_SERVICE_UNAVAILABLE", "down"))
                .when(productCatalogClient).requireProduct(70L, "admin-token");

        mockMvc.perform(put("/api/admin/inventory/70").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availableQuantity\": 5}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRODUCT_SERVICE_UNAVAILABLE"));
    }

    @Test
    void negativeStockIsRejectedByValidation() throws Exception {
        mockMvc.perform(put("/api/admin/inventory/1").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availableQuantity\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("availableQuantity"));
    }

    @Test
    void adjustAddsAndRemovesButNeverGoesNegative() throws Exception {
        mockMvc.perform(post("/api/admin/inventory/3/adjust").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\": 4, \"reason\": \"delivery\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(7));

        mockMvc.perform(post("/api/admin/inventory/3/adjust").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\": -8, \"reason\": \"damaged\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(itemRepository.findById(3L).orElseThrow().getAvailableQuantity()).isEqualTo(7);
    }

    @Test
    void adjustingAnUnknownProductIsNotFound() throws Exception {
        mockMvc.perform(post("/api/admin/inventory/404/adjust").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\": 1, \"reason\": \"delivery\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanInspectAnOrdersReservations() throws Exception {
        reservationService.reserve(42L, List.of(new ReservationLine(1L, 2), new ReservationLine(3L, 1)));

        mockMvc.perform(get("/api/admin/inventory/reservations").param("orderId", "42").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productId", contains(1, 3)))
                .andExpect(jsonPath("$[*].status", contains("RESERVED", "RESERVED")));

        mockMvc.perform(get("/api/admin/inventory/1").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(6))
                .andExpect(jsonPath("$.reservedQuantity").value(2));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(jwt -> jwt.tokenValue("admin-token").subject("1").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().jwt(jwt -> jwt.subject("2").claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}

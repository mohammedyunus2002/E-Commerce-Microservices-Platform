package com.ecommerce.order.web;

import com.ecommerce.order.client.CatalogProduct;
import com.ecommerce.order.client.ProductCatalogClient;
import com.ecommerce.order.repository.CartRepository;
import com.ecommerce.order.repository.CustomerOrderRepository;
import com.ecommerce.order.repository.OrderStatusChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
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
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerOrderRepository orderRepository;
    @Autowired
    private OrderStatusChangeRepository historyRepository;
    @Autowired
    private CartRepository cartRepository;

    @MockitoBean
    private ProductCatalogClient productCatalogClient;

    @BeforeEach
    void clean() {
        historyRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        when(productCatalogClient.getActiveProduct(1L))
                .thenReturn(new CatalogProduct(1L, "HEAD-100", "Headphones", new BigDecimal("10.00"), true));
        when(productCatalogClient.getActiveProduct(2L))
                .thenReturn(new CatalogProduct(2L, "CABLE-1", "Cable", new BigDecimal("3.50"), true));
    }

    @Test
    void placeSnapshotsCatalogPricesAndStaysCreated() throws Exception {
        mockMvc.perform(post("/api/orders").with(customer(8L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lines":[{"productId":1,"quantity":2},{"productId":1,"quantity":1},{"productId":2,"quantity":1}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.userId").value(8))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totalAmount").value(33.50))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].unitPrice").value(10.00))
                .andExpect(jsonPath("$.items[1].sku").value("CABLE-1"));
    }

    @Test
    void ordersRequireAuthenticationAndStayPrivate() throws Exception {
        String body = mockMvc.perform(post("/api/orders").with(customer(8L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders/" + id).with(customer(9L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/orders/" + id).with(customer(8L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"));
        mockMvc.perform(get("/api/admin/orders/" + id).with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void cancelCreatedOrderOnce() throws Exception {
        String body = mockMvc.perform(post("/api/orders").with(customer(8L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(post("/api/orders/" + id + "/cancel").with(customer(8L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/orders/" + id + "/cancel").with(customer(8L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    void checkoutPricesTheCartAndEmptiesIt() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(customer(8L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        mockMvc.perform(post("/api/cart/items").with(customer(8L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        mockMvc.perform(post("/api/orders/checkout").with(customer(8L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(30.00))
                .andExpect(jsonPath("$.status").value("CREATED"));

        mockMvc.perform(get("/api/cart").with(customer(8L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(post("/api/orders/checkout").with(customer(8L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
    }

    @Test
    void removingACartLineLeavesTheRest() throws Exception {
        mockMvc.perform(put("/api/cart/items").with(customer(8L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2,\"quantity\":4}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/cart/items/2").with(customer(8L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    private static RequestPostProcessor customer(long id) {
        return jwt().jwt(jwt -> jwt.subject(Long.toString(id)).claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(jwt -> jwt.subject("1").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}

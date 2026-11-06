package com.ecommerce.notification.web;

import com.ecommerce.notification.repository.NotificationRepository;
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

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void clean() {
        notificationRepository.deleteAll();
    }

    @Test
    void adminRecordsAMessageTheCustomerCanRead() throws Exception {
        mockMvc.perform(post("/api/admin/notifications").with(customer(8L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/notifications").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":8,"orderId":4,"type":"ORDER_RECEIVED","subject":"We got your order","body":"Order 4 is created."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ORDER_RECEIVED"))
                .andExpect(jsonPath("$.orderId").value(4));

        mockMvc.perform(get("/api/notifications").with(customer(8L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("We got your order"));
        mockMvc.perform(get("/api/notifications").with(customer(9L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
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

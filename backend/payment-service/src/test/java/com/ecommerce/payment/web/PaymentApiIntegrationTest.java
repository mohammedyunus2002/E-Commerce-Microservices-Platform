package com.ecommerce.payment.web;

import com.ecommerce.payment.client.OrderClient;
import com.ecommerce.payment.client.OrderSnapshot;
import com.ecommerce.payment.repository.PaymentRepository;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private OrderClient orderClient;

    @BeforeEach
    void clean() {
        paymentRepository.deleteAll();
    }

    @Test
    void chargeCapturesTheOrderTotalAndARepeatDoesNotChargeAgain() throws Exception {
        when(orderClient.getOwnOrder(eq(4L), anyString()))
                .thenReturn(new OrderSnapshot(4L, 8L, "CREATED", "USD", new BigDecimal("19.90")));

        mockMvc.perform(post("/api/payments").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.amount").value(19.90))
                .andExpect(jsonPath("$.failureCode").value(nullValue()));

        mockMvc.perform(post("/api/payments").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void amountEndingInThirteenCentsIsDeclinedThenRefundIsRejected() throws Exception {
        when(orderClient.getOwnOrder(eq(5L), anyString()))
                .thenReturn(new OrderSnapshot(5L, 8L, "CREATED", "USD", new BigDecimal("10.13")));

        mockMvc.perform(post("/api/payments").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("CARD_DECLINED"));

        mockMvc.perform(post("/api/admin/payments/5/refund").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_REFUNDABLE"));
    }

    @Test
    void refundOfACapturedPaymentIsIdempotent() throws Exception {
        when(orderClient.getOwnOrder(eq(6L), anyString()))
                .thenReturn(new OrderSnapshot(6L, 8L, "CREATED", "USD", new BigDecimal("8.00")));
        mockMvc.perform(post("/api/payments").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":6}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/payments/6/refund").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(post("/api/admin/payments/6/refund").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(get("/api/payments/6").with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(get("/api/payments/6").with(customer(9L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void chargingAnOrderThatIsNotCreatedIsRejected() throws Exception {
        when(orderClient.getOwnOrder(eq(7L), anyString()))
                .thenReturn(new OrderSnapshot(7L, 8L, "CONFIRMED", "USD", new BigDecimal("8.00")));

        mockMvc.perform(post("/api/payments").with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
    }

    private static RequestPostProcessor customer() {
        return customer(8L);
    }

    private static RequestPostProcessor customer(long id) {
        return jwt().jwt(jwt -> jwt.tokenValue("customer-token").subject(Long.toString(id))
                        .claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(jwt -> jwt.subject("1").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}

package com.ecommerce.order;

import com.ecommerce.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void checkout_shouldReturnCheckoutUrl_whenCartHasItemsAndStockIsEnough() throws Exception {
        String token = "user-token";

        when(orderService.checkout("Bearer " + token, null))
                .thenReturn(new CheckoutResponse(1L, "https://checkout.stripe.test/session"));

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1L))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.test/session"));
    }

    @Test
    void checkout_shouldFail_whenCartIsEmpty() throws Exception {
        String token = "user-token";

        when(orderService.checkout("Bearer " + token, null))
                .thenThrow(new RuntimeException("Cart is empty"));

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cart is empty"));
    }

    @Test
    void checkout_shouldFail_whenStockIsNotEnough() throws Exception {
        String token = "user-token";

        when(orderService.checkout("Bearer " + token, null))
                .thenThrow(new RuntimeException("Not enough stock for product: iPhone"));

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Not enough stock for product: iPhone"));
    }
}

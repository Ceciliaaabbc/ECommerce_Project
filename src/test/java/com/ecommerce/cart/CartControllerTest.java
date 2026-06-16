package com.ecommerce.cart;

import com.ecommerce.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartItemRepository cartItemRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void addToCart_shouldCreateNewItem_whenProductNotInCart() throws Exception {
        when(jwtUtil.getEmailFromToken("user-token")).thenReturn("user@example.com");
        when(cartItemRepository.findByUserEmailAndProductId("user@example.com", 1L))
                .thenReturn(Optional.empty());

        CartItem savedItem = new CartItem("user@example.com", 1L, "iPhone", new java.math.BigDecimal("999.00"), 1);
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedItem);

        mockMvc.perform(post("/api/cart")
                        .header("Authorization", "Bearer user-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": 1,
                                  "title": "iPhone",
                                  "price": 999.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value("user@example.com"))
                .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    void addToCart_shouldIncreaseQuantity_whenProductAlreadyExists() throws Exception {
        when(jwtUtil.getEmailFromToken("user-token")).thenReturn("user@example.com");

        CartItem existingItem = new CartItem("user@example.com", 1L, "iPhone", new java.math.BigDecimal("999.00"), 2);

        when(cartItemRepository.findByUserEmailAndProductId("user@example.com", 1L))
                .thenReturn(Optional.of(existingItem));

        when(cartItemRepository.save(any(CartItem.class))).thenReturn(existingItem);

        mockMvc.perform(post("/api/cart")
                        .header("Authorization", "Bearer user-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": 1,
                                  "title": "iPhone",
                                  "price": 999.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));
    }
}
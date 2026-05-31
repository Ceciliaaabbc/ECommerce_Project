package com.ecommerce.order;

import com.ecommerce.cart.CartItem;
import com.ecommerce.cart.CartItemRepository;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.security.JwtUtil;
import com.ecommerce.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private OrderItemRepository orderItemRepository;

    @MockBean
    private CartItemRepository cartItemRepository;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void checkout_shouldCreateOrder_whenCartHasItemsAndStockIsEnough() throws Exception {
        String token = "user-token";
        String email = "user@example.com";

        CartItem cartItem = new CartItem(email, 1L, "iPhone", 999.0, 2);
        Product product = new Product("iPhone", "Phone", 999.0, "image.jpg", 10);
        Order savedOrder = new Order(email, 1998.0, "PENDING");

        when(jwtUtil.getEmailFromToken(token)).thenReturn(email);
        when(cartItemRepository.findByUserEmail(email)).thenReturn(List.of(cartItem));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(new OrderItem());

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value(email))
                .andExpect(jsonPath("$.total").value(1998.0))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(productRepository, atLeastOnce()).save(product);
        verify(orderItemRepository).save(any(OrderItem.class));
        verify(cartItemRepository).deleteAll(List.of(cartItem));
    }

    @Test
    void checkout_shouldFail_whenCartIsEmpty() throws Exception {
        String token = "user-token";
        String email = "user@example.com";

        when(jwtUtil.getEmailFromToken(token)).thenReturn(email);
        when(cartItemRepository.findByUserEmail(email)).thenReturn(List.of());

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cart is empty"));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void checkout_shouldFail_whenStockIsNotEnough() throws Exception {
        String token = "user-token";
        String email = "user@example.com";

        CartItem cartItem = new CartItem(email, 1L, "iPhone", 999.0, 5);
        Product product = new Product("iPhone", "Phone", 999.0, "image.jpg", 2);

        when(jwtUtil.getEmailFromToken(token)).thenReturn(email);
        when(cartItemRepository.findByUserEmail(email)).thenReturn(List.of(cartItem));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Not enough stock for product: iPhone"));

        verify(orderRepository, never()).save(any(Order.class));
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }
}
package com.ecommerce.cart;

import com.ecommerce.security.JwtUtil;
import com.ecommerce.order.InventoryService;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.product.ProductVariant;
import com.ecommerce.product.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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
    private ProductRepository productRepository;

    @MockBean
    private ProductVariantRepository productVariantRepository;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void addToCart_shouldCreateNewItem_whenProductNotInCart() throws Exception {
        when(jwtUtil.getEmailFromToken("user-token")).thenReturn("user@example.com");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "iPhone", "999.00")));
        when(cartItemRepository.findByUserEmailAndProductIdAndVariantIdIsNull("user@example.com", 1L))
                .thenReturn(java.util.List.of());

        CartItem savedItem = new CartItem("user@example.com", 1L, "iPhone", new BigDecimal("999.00"), 1);
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
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "iPhone", "999.00")));

        CartItem existingItem = new CartItem("user@example.com", 1L, "iPhone", new BigDecimal("999.00"), 2);

        when(cartItemRepository.findByUserEmailAndProductIdAndVariantIdIsNull("user@example.com", 1L))
                .thenReturn(java.util.List.of(existingItem));

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

    @Test
    void addToCart_shouldKeepDifferentVariantsSeparate() throws Exception {
        when(jwtUtil.getEmailFromToken("user-token")).thenReturn("user@example.com");

        Product product = product(1L, "iPad", "799.00");
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("IPAD-BLUE-128");
        variant.setOptionName("Color");
        variant.setOptionValue("Blue");
        variant.setPrice(new BigDecimal("899.00"));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByUserEmailAndProductIdAndVariantId("user@example.com", 1L, 10L))
                .thenReturn(Optional.empty());

        CartItem savedItem = new CartItem("user@example.com", 1L, "iPad", new BigDecimal("899.00"), 1);
        savedItem.setVariantId(10L);
        savedItem.setSku("IPAD-BLUE-128");
        savedItem.setVariantName("Color: Blue");
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedItem);

        mockMvc.perform(post("/api/cart")
                        .header("Authorization", "Bearer user-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": 1,
                                  "variantId": 10,
                                  "title": "iPad",
                                  "price": 799.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("IPAD-BLUE-128"))
                .andExpect(jsonPath("$.variantName").value("Color: Blue"))
                .andExpect(jsonPath("$.price").value(899.0));
    }

    private Product product(Long id, String title, String price) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setTitle(title);
        product.setPrice(new BigDecimal(price));
        product.setStock(10);
        product.setReservedStock(0);
        return product;
    }
}

package com.ecommerce.product;

import com.ecommerce.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private ProductImageService productImageService;

    @MockBean
    private ProductCacheService productCacheService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void getAllProducts_shouldReturnProducts() throws Exception {
        Product product = new Product("iPhone", "Phone", new java.math.BigDecimal("999.00"), "image.jpg", 10);
        product.setCategory("Electronics");

        when(productCacheService.getAllProductsFromCache()).thenReturn(null);
        when(productRepository.findAll()).thenReturn(List.of(product));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("iPhone"))
                .andExpect(jsonPath("$[0].price").value(999.0));
    }

    @Test
    void createProduct_shouldWork_whenUserIsAdmin() throws Exception {
        Product saved = new Product("MacBook", "Laptop", new java.math.BigDecimal("1999.00"), "mac.jpg", 5);
        saved.setCategory("Electronics");

        when(productRepository.save(any(Product.class))).thenReturn(saved);

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "MacBook",
                                  "description": "Laptop",
                                  "price": 1999.0,
                                  "imageUrl": "mac.jpg",
                                  "stock": 5,
                                  "category": "Electronics"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("MacBook"));
    }

    @Test
    void browseProducts_shouldApplyPriceDescSort() throws Exception {
        Product expensive = new Product("iPad", "Tablet", new java.math.BigDecimal("7999.00"), "ipad.jpg", 10);
        Product cheap = new Product("Switch", "Console", new java.math.BigDecimal("4999.00"), "switch.jpg", 10);

        when(productRepository.findAll(any(Specification.class), org.mockito.ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of(expensive, cheap)));

        mockMvc.perform(get("/api/products/browse")
                        .param("sort", "priceDesc")
                        .param("page", "0")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("iPad"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), pageableCaptor.capture());

        Sort.Order priceOrder = pageableCaptor.getValue().getSort().getOrderFor("price");
        Sort.Order idOrder = pageableCaptor.getValue().getSort().getOrderFor("id");
        assertEquals(Sort.Direction.DESC, priceOrder.getDirection());
        assertEquals(Sort.Direction.DESC, idOrder.getDirection());
    }

}

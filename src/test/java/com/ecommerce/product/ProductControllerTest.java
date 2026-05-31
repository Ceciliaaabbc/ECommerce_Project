package com.ecommerce.product;

import com.ecommerce.security.JwtUtil;
import com.ecommerce.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void getAllProducts_shouldReturnProducts() throws Exception {
        Product product = new Product("iPhone", "Phone", 999.0, "image.jpg", 10);
        product.setCategory("Electronics");

        when(productRepository.findAll()).thenReturn(List.of(product));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("iPhone"))
                .andExpect(jsonPath("$[0].price").value(999.0));
    }

    @Test
    void createProduct_shouldWork_whenUserIsAdmin() throws Exception {
        when(jwtUtil.getRoleFromToken("admin-token")).thenReturn("ADMIN");

        Product saved = new Product("MacBook", "Laptop", 1999.0, "mac.jpg", 5);
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
      void createProduct_shouldFail_whenUserIsNotAdmin() throws Exception {
          when(jwtUtil.getRoleFromToken("user-token")).thenReturn("USER");

          mockMvc.perform(post("/api/products")
                          .header("Authorization", "Bearer user-token")
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
                  .andExpect(status().isBadRequest())
                  .andExpect(content().string("Only admin can do this"));
      }


}
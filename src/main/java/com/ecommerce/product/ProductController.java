package com.ecommerce.product;

import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import org.springframework.web.bind.annotation.*;
import com.ecommerce.security.JwtUtil;// 前端必须传 JWT token, 后端从 token 判断是否 ADMIN

import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public ProductController(ProductRepository productRepository,UserRepository userRepository, JwtUtil jwtUtil) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @PostMapping
    public Product createProduct( @RequestHeader("Authorization") String authHeader, @RequestBody Product product ) {
        checkAdmin(authHeader);
        return productRepository.save(product);
    }

    @PutMapping("/{id}")
    public Product updateProduct( @PathVariable Long id, @RequestHeader("Authorization") String authHeader,
            @RequestBody Product updatedProduct) {
        checkAdmin(authHeader);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setTitle(updatedProduct.getTitle());
        product.setCategory(updatedProduct.getCategory());
        product.setDescription(updatedProduct.getDescription());
        product.setPrice(updatedProduct.getPrice());
        product.setImageUrl(updatedProduct.getImageUrl());
        product.setStock(updatedProduct.getStock());

        return productRepository.save(product);
    }

    @DeleteMapping("/{id}")
    public String deleteProduct(@PathVariable Long id,@RequestHeader("Authorization") String authHeader ) {
        checkAdmin(authHeader);

        productRepository.deleteById(id);
        return "Product deleted";
    }

     @GetMapping("/search")
    public List<Product> searchProducts(@RequestParam String keyword) {
        return productRepository.findByTitleContainingIgnoreCase(keyword);
    }

    @GetMapping("/category")
    public List<Product> getProductsByCategory(@RequestParam String category) {
        return productRepository.findByCategoryIgnoreCase(category);
    }

    private void checkAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing token");
        }

        String token = authHeader.substring(7);
        String role = jwtUtil.getRoleFromToken(token);

        if (!"ADMIN".equals(role)) {
            throw new RuntimeException("Only admin can do this");
        }
    }

   
}
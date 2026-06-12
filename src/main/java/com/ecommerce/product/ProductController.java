package com.ecommerce.product;

import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ecommerce.security.JwtUtil;// 前端必须传 JWT token, 后端从 token 判断是否 ADMIN
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final ProductImageService productImageService;

    public ProductController(ProductRepository productRepository,UserRepository userRepository, JwtUtil jwtUtil, ProductImageService productImageService) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.productImageService = productImageService;
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
    public Product updateProduct(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Product updatedProduct
    ) {
        checkAdmin(authHeader);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setTitle(updatedProduct.getTitle());
        product.setCategory(updatedProduct.getCategory());
        product.setDescription(updatedProduct.getDescription());
        product.setPrice(updatedProduct.getPrice());
        product.setStock(updatedProduct.getStock());

        if (updatedProduct.getImageUrl() != null && !updatedProduct.getImageUrl().isBlank()) {
            product.setImageUrl(updatedProduct.getImageUrl());
        }

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


    @PostMapping("/{id}/image")
    public Product uploadProductImage(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("image") MultipartFile image
    ) throws Exception {
        checkAdmin(authHeader);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        String imageUrl = productImageService.uploadProductImage(image);

        System.out.println("Uploaded image URL: " + imageUrl);

        product.setImageUrl(imageUrl);

        Product savedProduct = productRepository.save(product);

        System.out.println("Saved product imageUrl: " + savedProduct.getImageUrl());

        return savedProduct;
    }

    @RequestMapping(value = "/{id}/image", method = RequestMethod.OPTIONS)
    public void handleImageUploadPreflight() {
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
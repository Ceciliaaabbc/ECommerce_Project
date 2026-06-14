package com.ecommerce.product;

import com.ecommerce.user.UserRepository;
import com.ecommerce.security.JwtUtil;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final ProductImageService productImageService;
    private final ProductCacheService productCacheService;

    public ProductController(
            ProductRepository productRepository,
            UserRepository userRepository,
            JwtUtil jwtUtil,
            ProductImageService productImageService,
            ProductCacheService productCacheService
    ) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.productImageService = productImageService;
        this.productCacheService = productCacheService;
    }

    @GetMapping
    public Object getAllProducts() {
        Object cachedProducts = productCacheService.getAllProductsFromCache();

        if (cachedProducts != null) {
            System.out.println("Redis cache hit: all products");
            return cachedProducts;
        }

        System.out.println("Redis cache miss: all products");

        List<Product> products = productRepository.findAll();
        productCacheService.cacheAllProducts(products);

        return products;
    }

    @GetMapping("/{id}")
    public Object getProductById(@PathVariable Long id) {
        Object cachedProduct = productCacheService.getProductFromCache(id);

        if (cachedProduct != null) {
            System.out.println("Redis cache hit: product " + id);
            return cachedProduct;
        }

        System.out.println("Redis cache miss: product " + id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        productCacheService.cacheProduct(product);

        return product;
    }

    @PostMapping
    public Product createProduct(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Product product
    ) {
        checkAdmin(authHeader);

        Product savedProduct = productRepository.save(product);

        productCacheService.clearAllProductsCache();

        return savedProduct;
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

        Product savedProduct = productRepository.save(product);

        productCacheService.clearProductCaches(id);

        return savedProduct;
    }

    @DeleteMapping("/{id}")
    public String deleteProduct(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        checkAdmin(authHeader);

        productRepository.deleteById(id);

        productCacheService.clearProductCaches(id);

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

        product.setImageUrl(imageUrl);

        Product savedProduct = productRepository.save(product);

        productCacheService.clearProductCaches(id);

        return savedProduct;
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
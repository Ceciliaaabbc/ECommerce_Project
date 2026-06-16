package com.ecommerce.product;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductImageService productImageService;
    private final ProductCacheService productCacheService;

    public ProductController(
            ProductRepository productRepository,
            ProductImageService productImageService,
            ProductCacheService productCacheService
    ) {
        this.productRepository = productRepository;
        this.productImageService = productImageService;
        this.productCacheService = productCacheService;
    }

    @GetMapping
    public Object getAllProducts() {
        System.out.println("NEW REDIS PRODUCT CONTROLLER IS RUNNING");
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
    @PreAuthorize("hasRole('ADMIN')")
    public Product createProduct(
            @RequestBody Product product
    ) {

        Product savedProduct = productRepository.save(product);

        productCacheService.clearAllProductsCache();

        return savedProduct;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Product updateProduct(
            @PathVariable Long id,
            @RequestBody Product updatedProduct
    ) {

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
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteProduct(
            @PathVariable Long id
    ) {

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
    @PreAuthorize("hasRole('ADMIN')")
    public Product uploadProductImage(
            @PathVariable Long id,
            @RequestParam("image") MultipartFile image
    ) throws Exception {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        String imageUrl = productImageService.uploadProductImage(image);

        product.setImageUrl(imageUrl);

        Product savedProduct = productRepository.save(product);

        productCacheService.clearProductCaches(id);

        return savedProduct;
    }

}
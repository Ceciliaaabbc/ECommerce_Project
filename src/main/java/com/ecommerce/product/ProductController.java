package com.ecommerce.product;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
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


    @GetMapping("/browse")
    public Page<Product> browseProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "createdDesc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);

        Specification<Product> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), likeKeyword)
                ));
            }

            if (category != null && !category.isBlank()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("category")), category.trim().toLowerCase()));
            }

            if (minPrice != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(safePage, safeSize, getProductSort(sort));
        return productRepository.findAll(specification, pageable);
    }

    @GetMapping("/{id:\\d+}")
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

    @PutMapping("/{id:\\d+}")
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

    @DeleteMapping("/{id:\\d+}")
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

    @PostMapping("/{id:\\d+}/image")
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


    private Sort getProductSort(String sort) {
        return switch (sort) {
            case "priceAsc" -> Sort.by(Sort.Direction.ASC, "price");
            case "priceDesc" -> Sort.by(Sort.Direction.DESC, "price");
            case "titleAsc" -> Sort.by(Sort.Direction.ASC, "title");
            case "stockDesc" -> Sort.by(Sort.Direction.DESC, "stock");
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
    }

}

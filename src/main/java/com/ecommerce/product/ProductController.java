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
        products.forEach(this::initializeProductChildren);
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
        initializeProductChildren(product);

        productCacheService.cacheProduct(product);

        return product;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Product createProduct(
            @RequestBody Product product
    ) {

        normalizeProductChildren(product);
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
        product.setImageUrl(updatedProduct.getImageUrl());
        product.setVariants(updatedProduct.getVariants());
        product.setImages(updatedProduct.getImages());
        normalizeProductChildren(product);

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

        ProductImage productImage = new ProductImage();
        productImage.setImageUrl(imageUrl);
        productImage.setSortOrder(product.getImages().size());
        productImage.setPrimaryImage(product.getImages().isEmpty());
        product.addImage(productImage);
        if (product.getImageUrl() == null || product.getImageUrl().isBlank()) {
            product.setImageUrl(imageUrl);
        }
        normalizeProductChildren(product);

        Product savedProduct = productRepository.save(product);

        productCacheService.clearProductCaches(id);

        return savedProduct;
    }


    private void initializeProductChildren(Product product) {
        product.getImages().size();
        product.getVariants().size();
    }

    private void normalizeProductChildren(Product product) {
        List<ProductImage> images = product.getImages();
        boolean hasPrimaryImage = images.stream().anyMatch(image -> Boolean.TRUE.equals(image.getPrimaryImage()));
        for (int index = 0; index < images.size(); index++) {
            ProductImage image = images.get(index);
            image.setProduct(product);
            if (image.getSortOrder() == null) {
                image.setSortOrder(index);
            }
            if (!hasPrimaryImage && index == 0) {
                image.setPrimaryImage(true);
            }
        }

        product.getVariants().forEach(variant -> {
            variant.setProduct(product);
            if (variant.getSku() != null && variant.getSku().isBlank()) {
                variant.setSku(null);
            }
            if (variant.getActive() == null) {
                variant.setActive(true);
            }
        });

        product.getImages().stream()
                .filter(image -> Boolean.TRUE.equals(image.getPrimaryImage()))
                .findFirst()
                .map(ProductImage::getImageUrl)
                .ifPresent(product::setImageUrl);
    }

    private Sort getProductSort(String sort) {
        return switch (sort) {
            case "priceAsc" -> Sort.by(Sort.Direction.ASC, "price").and(Sort.by(Sort.Direction.ASC, "id"));
            case "priceDesc" -> Sort.by(Sort.Direction.DESC, "price").and(Sort.by(Sort.Direction.DESC, "id"));
            case "titleAsc" -> Sort.by(Sort.Direction.ASC, "title").and(Sort.by(Sort.Direction.ASC, "id"));
            case "stockDesc" -> Sort.by(Sort.Direction.DESC, "stock").and(Sort.by(Sort.Direction.DESC, "id"));
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
    }

}

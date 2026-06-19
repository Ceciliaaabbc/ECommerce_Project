package com.ecommerce.product;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "products")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    @Column(precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url")
    private String imageUrl;

    private Integer stock;

    private Integer reservedStock = 0;

    private String category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductImage> images = new ArrayList<>();

    public Product() {
    }

    public Product(String title, String description, BigDecimal price, String imageUrl, Integer stock) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getImageUrl() {
        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl;
        }
        return images.stream()
                .filter(image -> Boolean.TRUE.equals(image.getPrimaryImage()))
                .findFirst()
                .or(() -> images.stream().min(Comparator.comparing(image -> image.getSortOrder() == null ? 0 : image.getSortOrder())))
                .map(ProductImage::getImageUrl)
                .orElse(imageUrl);
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getReservedStock() {
        return reservedStock == null ? 0 : reservedStock;
    }

    public void setReservedStock(Integer reservedStock) {
        this.reservedStock = reservedStock;
    }

    public Integer getAvailableStock() {
        return (stock == null ? 0 : stock) - getReservedStock();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public void setVariants(List<ProductVariant> variants) {
        this.variants.clear();
        if (variants == null) {
            return;
        }
        variants.forEach(this::addVariant);
    }

    public void addVariant(ProductVariant variant) {
        variant.setProduct(this);
        this.variants.add(variant);
    }

    public List<ProductImage> getImages() {
        return images;
    }

    public void setImages(List<ProductImage> images) {
        this.images.clear();
        if (images == null) {
            return;
        }
        images.forEach(this::addImage);
    }

    public void addImage(ProductImage image) {
        image.setProduct(this);
        this.images.add(image);
    }
}

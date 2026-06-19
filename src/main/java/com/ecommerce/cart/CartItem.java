package com.ecommerce.cart;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userEmail;

    private Long productId;

    private Long variantId;

    private String sku;

    private String variantName;

    private String title;

    @Column(precision = 19, scale = 2)
    private BigDecimal price;

    private Integer quantity;

    public CartItem() {
    }

    public CartItem(String userEmail, Long productId, String title, BigDecimal price, Integer quantity) {
        this.userEmail = userEmail;
        this.productId = productId;
        this.title = title;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public String getSku() {
        return sku;
    }

    public String getVariantName() {
        return variantName;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}

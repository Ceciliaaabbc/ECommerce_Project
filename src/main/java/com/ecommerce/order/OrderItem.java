package com.ecommerce.order;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private Long productId;

    private Long variantId;

    private String sku;

    private String variantName;

    private String title;

    @Column(precision = 19, scale = 2)
    private BigDecimal price;

    private Integer quantity;

    public OrderItem() {
    }

    public OrderItem(Long orderId, Long productId, String title, BigDecimal price, Integer quantity) {
        this(orderId, productId, null, null, null, title, price, quantity);
    }

    public OrderItem(
            Long orderId,
            Long productId,
            Long variantId,
            String sku,
            String variantName,
            String title,
            BigDecimal price,
            Integer quantity
    ) {
        this.orderId = orderId;
        this.productId = productId;
        this.variantId = variantId;
        this.sku = sku;
        this.variantName = variantName;
        this.title = title;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
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
}

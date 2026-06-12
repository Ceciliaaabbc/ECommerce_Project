package com.ecommerce.order;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userEmail;

    private Double total;

    private String status;

    private LocalDateTime createdAt;

    private String paymentStatus;
    private String stripeSessionId;

    

    public Order() {
    }

    public Order(String userEmail, Double total, String status) {
        this.userEmail = userEmail;
        this.total = total;
        this.status = status;
        this.paymentStatus = "UNPAID";
        this.createdAt = LocalDateTime.now();
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public Double getTotal() {
        return total;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }
}
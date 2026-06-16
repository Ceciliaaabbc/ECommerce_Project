package com.ecommerce.order;

public enum OrderStatus {
    PENDING,
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    PENDING_SHIPMENT,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REFUNDING,
    REFUNDED
}

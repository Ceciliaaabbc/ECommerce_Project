package com.ecommerce.order;

public enum OrderStatus {
    PENDING,
    PENDING_PAYMENT,
    PAID,
    PENDING_SHIPMENT,
    SHIPPED,
    COMPLETED,
    CANCELLED,
    REFUNDING,
    REFUNDED
}

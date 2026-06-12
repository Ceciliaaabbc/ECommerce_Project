package com.ecommerce.order;

public class CheckoutResponse {

    private Long orderId;
    private String checkoutUrl;

    public CheckoutResponse(Long orderId, String checkoutUrl) {
        this.orderId = orderId;
        this.checkoutUrl = checkoutUrl;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }
}
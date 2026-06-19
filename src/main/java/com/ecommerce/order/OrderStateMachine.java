package com.ecommerce.order;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAID, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.REFUNDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED, OrderStatus.REFUNDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING_SHIPMENT, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED, OrderStatus.REFUNDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED, OrderStatus.REFUNDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.REFUNDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.of(OrderStatus.REFUNDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUNDING, EnumSet.of(OrderStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));
    }

    public void applyTransition(Order order, OrderStatus nextStatus) {
        validateTransition(order, nextStatus);
        applyPaymentStatus(order, nextStatus);
        order.setStatus(nextStatus);
    }

    public void validateTransition(Order order, OrderStatus nextStatus) {
        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == nextStatus) {
            return;
        }

        Set<OrderStatus> allowedNextStatuses = ALLOWED_TRANSITIONS.getOrDefault(
                currentStatus,
                EnumSet.noneOf(OrderStatus.class)
        );

        if (!allowedNextStatuses.contains(nextStatus)) {
            throw new RuntimeException("Invalid order status transition: " + currentStatus + " -> " + nextStatus);
        }

        if (PaymentStatus.UNPAID.equals(order.getPaymentStatus()) && requiresPaidOrder(nextStatus)) {
            throw new RuntimeException("Order must be paid before moving to " + nextStatus);
        }
    }

    private boolean requiresPaidOrder(OrderStatus status) {
        return OrderStatus.PROCESSING.equals(status)
                || OrderStatus.PENDING_SHIPMENT.equals(status)
                || OrderStatus.SHIPPED.equals(status)
                || OrderStatus.DELIVERED.equals(status)
                || OrderStatus.COMPLETED.equals(status);
    }

    private void applyPaymentStatus(Order order, OrderStatus status) {
        if (OrderStatus.CANCELLED.equals(status) && !PaymentStatus.PAID.equals(order.getPaymentStatus())) {
            order.setPaymentStatus(PaymentStatus.CANCELLED);
        }

        if (OrderStatus.REFUNDED.equals(status)) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }
    }
}

package com.ecommerce.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    void applyTransition_shouldMovePaidProcessingOrderToShipped() {
        Order order = new Order("buyer@example.com", java.math.BigDecimal.TEN, OrderStatus.PROCESSING);
        order.setPaymentStatus(PaymentStatus.PAID);

        stateMachine.applyTransition(order, OrderStatus.SHIPPED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void applyTransition_shouldRejectUnpaidOrderMovingToProcessing() {
        Order order = new Order("buyer@example.com", java.math.BigDecimal.TEN, OrderStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> stateMachine.applyTransition(order, OrderStatus.PROCESSING))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order must be paid");
    }

    @Test
    void applyTransition_shouldRejectInvalidJump() {
        Order order = new Order("buyer@example.com", java.math.BigDecimal.TEN, OrderStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> stateMachine.applyTransition(order, OrderStatus.SHIPPED))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid order status transition");
    }

    @Test
    void applyTransition_shouldMarkUnpaidCancelledOrderAsCancelledPayment() {
        Order order = new Order("buyer@example.com", java.math.BigDecimal.TEN, OrderStatus.PENDING_PAYMENT);

        stateMachine.applyTransition(order, OrderStatus.CANCELLED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }
}

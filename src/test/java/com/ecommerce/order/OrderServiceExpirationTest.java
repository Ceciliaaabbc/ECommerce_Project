package com.ecommerce.order;

import com.ecommerce.cart.CartItemRepository;
import com.ecommerce.security.JwtUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceExpirationTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
    private final CartItemRepository cartItemRepository = mock(CartItemRepository.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final OrderStateMachine orderStateMachine = new OrderStateMachine();
    private final OrderAddressSnapshotService snapshotService = mock(OrderAddressSnapshotService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);

    private final OrderService orderService = new OrderService(
            orderRepository,
            orderItemRepository,
            cartItemRepository,
            inventoryService,
            orderStateMachine,
            snapshotService,
            jwtUtil
    );

    @Test
    void expireUnpaidOrdersCreatedBefore_shouldExpireOrdersAndReleaseReservedInventory() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        Order order = new Order("buyer@example.com", BigDecimal.TEN, OrderStatus.PENDING_PAYMENT);
        order.setInventoryReserved(true);
        OrderItem item = new OrderItem(99L, 5L, "Phone", BigDecimal.TEN, 2);

        when(orderRepository.findByStatusAndPaymentStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT,
                PaymentStatus.UNPAID,
                cutoff
        )).thenReturn(List.of(order));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(item));

        int expiredCount = orderService.expireUnpaidOrdersCreatedBefore(cutoff);

        assertThat(expiredCount).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(order.isInventoryReserved()).isFalse();
        verify(inventoryService).releaseReservedStock(5L, null, 2);
        verify(orderRepository).saveAll(List.of(order));
    }
}

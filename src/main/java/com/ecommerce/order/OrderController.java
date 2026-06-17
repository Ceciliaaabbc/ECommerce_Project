package com.ecommerce.order;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Long shippingAddressId
    ) throws Exception {
        return orderService.checkout(authHeader, shippingAddressId);
    }

    @GetMapping
    public List<Order> getOrders(@RequestHeader("Authorization") String authHeader) {
        return orderService.getOrders(authHeader);
    }

    @GetMapping("/{orderId}")
    public Order getOrder(
            @PathVariable Long orderId,
            @RequestHeader("Authorization") String authHeader
    ) {
        return orderService.getOrder(orderId, authHeader);
    }

    @GetMapping("/{orderId}/items")
    public List<OrderItem> getOrderItems(
            @PathVariable Long orderId,
            @RequestHeader("Authorization") String authHeader
    ) {
        return orderService.getOrderItems(orderId, authHeader);
    }

    @PostMapping("/{orderId}/pay")
    public CheckoutResponse payOrder(
            @PathVariable Long orderId,
            @RequestHeader("Authorization") String authHeader
    ) throws Exception {
        return orderService.payOrder(orderId, authHeader);
    }

    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Order updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status
    ) {
        return orderService.updateOrderStatus(orderId, status);
    }

    @PostMapping("/{orderId}/ship")
    @PreAuthorize("hasRole('ADMIN')")
    public Order shipOrder(
            @PathVariable Long orderId,
            @RequestBody ShipOrderRequest request
    ) {
        return orderService.shipOrder(orderId, request);
    }

    @PutMapping("/{orderId}/cancel-payment")
    public Order cancelPayment(
            @PathVariable Long orderId,
            @RequestHeader("Authorization") String authHeader
    ) {
        return orderService.cancelPayment(orderId, authHeader);
    }
}

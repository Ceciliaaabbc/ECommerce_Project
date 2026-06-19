package com.ecommerce.order;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;
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


    @GetMapping("/admin/search")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<Order> searchAdminOrders(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return orderService.searchAdminOrders(
                authHeader,
                status,
                paymentStatus,
                userEmail,
                parseStartOfDay(createdFrom),
                parseEndOfDay(createdTo),
                page,
                size
        );
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
            @RequestParam OrderStatus status,
            @RequestHeader("Authorization") String authHeader
    ) {
        return orderService.updateOrderStatus(orderId, status, authHeader);
    }

    @PostMapping("/{orderId}/ship")
    @PreAuthorize("hasRole('ADMIN')")
    public Order shipOrder(
            @PathVariable Long orderId,
            @RequestBody ShipOrderRequest request,
            @RequestHeader("Authorization") String authHeader
    ) {
        return orderService.shipOrder(orderId, request, authHeader);
    }

    @PutMapping("/{orderId}/cancel-payment")
    public Order cancelPayment(
            @PathVariable Long orderId,
            @RequestHeader("Authorization") String authHeader
    ) {
        return orderService.cancelPayment(orderId, authHeader);
    }
    private LocalDateTime parseStartOfDay(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date).atStartOfDay();
    }

    private LocalDateTime parseEndOfDay(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date).plusDays(1).atStartOfDay().minusNanos(1);
    }

}

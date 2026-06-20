package com.ecommerce.order;

import com.ecommerce.cart.CartItem;
import com.ecommerce.cart.CartItemRepository;
import com.ecommerce.security.JwtUtil;
import jakarta.persistence.criteria.Predicate;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final InventoryService inventoryService;
    private final OrderStateMachine orderStateMachine;
    private final OrderAddressSnapshotService orderAddressSnapshotService;
    private final JwtUtil jwtUtil;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${order.expiration-minutes:30}")
    private long orderExpirationMinutes;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartItemRepository cartItemRepository,
            InventoryService inventoryService,
            OrderStateMachine orderStateMachine,
            OrderAddressSnapshotService orderAddressSnapshotService,
            JwtUtil jwtUtil
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.inventoryService = inventoryService;
        this.orderStateMachine = orderStateMachine;
        this.orderAddressSnapshotService = orderAddressSnapshotService;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public CheckoutResponse checkout(String authHeader, Long shippingAddressId) throws Exception {
        String userEmail = getEmail(authHeader);
        List<CartItem> cartItems = cartItemRepository.findByUserEmail(userEmail);

        if (cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : cartItems) {
            inventoryService.requireEnoughStock(item.getProductId(), item.getVariantId(), item.getQuantity());
            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        Order order = new Order(userEmail, total, OrderStatus.PENDING_PAYMENT);
        orderAddressSnapshotService.applyShippingAddressSnapshot(order, shippingAddressId, userEmail);
        Order savedOrder = orderRepository.save(order);

        for (CartItem item : cartItems) {
            orderItemRepository.save(new OrderItem(
                    savedOrder.getId(),
                    item.getProductId(),
                    item.getVariantId(),
                    item.getSku(),
                    item.getVariantName(),
                    item.getTitle(),
                    item.getPrice(),
                    item.getQuantity()
            ));
        }

        reserveOrderInventory(savedOrder);
        Session session = createCheckoutSession(savedOrder, total);
        savedOrder.setStripeSessionId(session.getId());
        orderRepository.save(savedOrder);

        return new CheckoutResponse(savedOrder.getId(), session.getUrl());
    }

    public List<Order> getOrders(String authHeader) {
        String token = getToken(authHeader);
        String userEmail = jwtUtil.getEmailFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        if ("ADMIN".equals(role)) {
            return orderRepository.findAll();
        }

        return orderRepository.findByUserEmail(userEmail);
    }


    public Page<Order> searchAdminOrders(
            String authHeader,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String userEmail,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            int page,
            int size
    ) {
        requireAdmin(authHeader);

        Specification<Order> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (paymentStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("paymentStatus"), paymentStatus));
            }

            if (userEmail != null && !userEmail.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("userEmail")),
                        "%" + userEmail.trim().toLowerCase() + "%"
                ));
            }

            if (createdFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }

            if (createdTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepository.findAll(specification, pageable);
    }

    public Order getOrder(Long orderId, String authHeader) {
        return getOrderForCurrentUser(orderId, authHeader);
    }

    public List<OrderItem> getOrderItems(Long orderId, String authHeader) {
        getOrderForCurrentUser(orderId, authHeader);
        return orderItemRepository.findByOrderId(orderId);
    }

    @Transactional
    public CheckoutResponse payOrder(Long orderId, String authHeader) throws Exception {
        Order order = getOrderForCurrentUser(orderId, authHeader);

        if (PaymentStatus.PAID.equals(order.getPaymentStatus())) {
            throw new RuntimeException("Order is already paid");
        }

        if (OrderStatus.CANCELLED.equals(order.getStatus()) || PaymentStatus.CANCELLED.equals(order.getPaymentStatus())) {
            throw new RuntimeException("Cancelled order cannot be paid");
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        if (orderItems.isEmpty()) {
            throw new RuntimeException("Order has no items");
        }

        if (!order.isInventoryReserved()) {
            reserveOrderInventory(order);
        }

        Session session = createCheckoutSession(order, order.getTotal());
        order.setStripeSessionId(session.getId());
        orderRepository.save(order);

        return new CheckoutResponse(order.getId(), session.getUrl());
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus status, String authHeader) {
        requireAdmin(authHeader);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        orderStateMachine.validateTransition(order, status);
        if (OrderStatus.CANCELLED.equals(status) && !PaymentStatus.PAID.equals(order.getPaymentStatus())) {
            releaseOrderInventory(order);
        }
        orderStateMachine.applyTransition(order, status);
        return orderRepository.save(order);
    }

    @Transactional
    public Order shipOrder(Long orderId, ShipOrderRequest request, String authHeader) {
        requireAdmin(authHeader);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!PaymentStatus.PAID.equals(order.getPaymentStatus())) {
            throw new RuntimeException("Order must be paid before shipping");
        }

        if (!OrderStatus.PROCESSING.equals(order.getStatus()) && !OrderStatus.PENDING_SHIPMENT.equals(order.getStatus())) {
            throw new RuntimeException("Only processing orders can be shipped");
        }

        if (request.getCarrier() == null || request.getCarrier().trim().isEmpty()) {
            throw new RuntimeException("Carrier is required");
        }

        if (request.getTrackingNumber() == null || request.getTrackingNumber().trim().isEmpty()) {
            throw new RuntimeException("Tracking number is required");
        }

        order.setCarrier(request.getCarrier().trim());
        order.setTrackingNumber(request.getTrackingNumber().trim());
        order.setShippedAt(LocalDateTime.now());
        orderStateMachine.applyTransition(order, OrderStatus.SHIPPED);
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelPayment(Long orderId, String authHeader) {
        String userEmail = getEmail(authHeader);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("You cannot cancel this order");
        }

        if (PaymentStatus.PAID.equals(order.getPaymentStatus())) {
            throw new RuntimeException("Paid order cannot be cancelled");
        }

        releaseOrderInventory(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.CANCELLED);
        return orderRepository.save(order);
    }

    @Transactional
    public Order refundOrder(Long orderId, String authHeader) {
        requireAdmin(authHeader);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!PaymentStatus.PAID.equals(order.getPaymentStatus())) {
            throw new RuntimeException("Only paid orders can be refunded");
        }

        order.setStatus(OrderStatus.REFUNDED);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        return orderRepository.save(order);
    }

    @Scheduled(fixedDelayString = "${order.expiration-scan-delay-ms:60000}")
    @Transactional
    public void expireOverdueUnpaidOrders() {
        expireUnpaidOrdersCreatedBefore(LocalDateTime.now().minusMinutes(orderExpirationMinutes));
    }

    @Transactional
    public int expireUnpaidOrdersCreatedBefore(LocalDateTime cutoff) {
        List<Order> overdueOrders = orderRepository.findByStatusAndPaymentStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT,
                PaymentStatus.UNPAID,
                cutoff
        );

        for (Order order : overdueOrders) {
            releaseOrderInventory(order);
            order.setStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.EXPIRED);
        }

        orderRepository.saveAll(overdueOrders);
        return overdueOrders.size();
    }

    private long toCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private void reserveOrderInventory(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        if (orderItems.isEmpty()) {
            throw new RuntimeException("Order has no items");
        }

        for (OrderItem item : orderItems) {
            inventoryService.reserveStock(item.getProductId(), item.getVariantId(), item.getQuantity());
        }

        order.setInventoryReserved(true);
        orderRepository.save(order);
    }

    private void releaseOrderInventory(Order order) {
        if (!order.isInventoryReserved()) {
            return;
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem item : orderItems) {
            inventoryService.releaseReservedStock(item.getProductId(), item.getVariantId(), item.getQuantity());
        }

        order.setInventoryReserved(false);
        orderRepository.save(order);
    }

    private Session createCheckoutSession(Order order, BigDecimal total) throws Exception {
        Stripe.apiKey = stripeSecretKey;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/orders/" + order.getId())
                .setCancelUrl(frontendUrl + "/orders/" + order.getId() + "?payment=cancelled")
                .setExpiresAt(LocalDateTime.now().plusMinutes(orderExpirationMinutes)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toEpochSecond())
                .setClientReferenceId(order.getId().toString())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(toCents(total))
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Order #" + order.getId())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        return Session.create(params);
    }

    private Order getOrderForCurrentUser(Long orderId, String authHeader) {
        String token = getToken(authHeader);
        String userEmail = jwtUtil.getEmailFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!"ADMIN".equals(role) && !order.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("You cannot view this order");
        }

        return order;
    }

    private String getEmail(String authHeader) {
        return jwtUtil.getEmailFromToken(getToken(authHeader));
    }

    private void requireAdmin(String authHeader) {
        String role = jwtUtil.getRoleFromToken(getToken(authHeader));
        if (!"ADMIN".equals(role)) {
            throw new RuntimeException("Admin access required");
        }
    }

    private String getToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing token");
        }
        return authHeader.substring(7);
    }
}

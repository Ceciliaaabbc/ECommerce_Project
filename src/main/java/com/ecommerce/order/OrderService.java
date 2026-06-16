package com.ecommerce.order;

import com.ecommerce.cart.CartItem;
import com.ecommerce.cart.CartItemRepository;
import com.ecommerce.security.JwtUtil;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_STATUS_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.PAID, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.REFUNDING));
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED, OrderStatus.REFUNDING));
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.PENDING_SHIPMENT, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED, OrderStatus.REFUNDING));
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED, OrderStatus.REFUNDING));
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.REFUNDING));
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.of(OrderStatus.REFUNDING));
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.REFUNDING, EnumSet.of(OrderStatus.REFUNDED));
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_STATUS_TRANSITIONS.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final InventoryService inventoryService;
    private final JwtUtil jwtUtil;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${frontend.url}")
    private String frontendUrl;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartItemRepository cartItemRepository,
            InventoryService inventoryService,
            JwtUtil jwtUtil
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.inventoryService = inventoryService;
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
            inventoryService.requireEnoughStock(item.getProductId(), item.getQuantity());
            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        Order order = new Order(userEmail, total, OrderStatus.PENDING_PAYMENT);
        order.setShippingAddressId(shippingAddressId);
        Order savedOrder = orderRepository.save(order);

        for (CartItem item : cartItems) {
            orderItemRepository.save(new OrderItem(
                    savedOrder.getId(),
                    item.getProductId(),
                    item.getTitle(),
                    item.getPrice(),
                    item.getQuantity()
            ));
        }

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

        for (OrderItem item : orderItems) {
            inventoryService.requireEnoughStock(item.getProductId(), item.getQuantity());
        }

        Session session = createCheckoutSession(order, order.getTotal());
        order.setStripeSessionId(session.getId());
        orderRepository.save(order);

        return new CheckoutResponse(order.getId(), session.getUrl());
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        validateStatusTransition(order, status);
        applyPaymentStatusForOrderStatus(order, status);
        order.setStatus(status);
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

        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.CANCELLED);
        return orderRepository.save(order);
    }

    private long toCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private void validateStatusTransition(Order order, OrderStatus nextStatus) {
        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == nextStatus) {
            return;
        }

        Set<OrderStatus> allowedNextStatuses = ALLOWED_STATUS_TRANSITIONS.getOrDefault(
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

    private void applyPaymentStatusForOrderStatus(Order order, OrderStatus status) {
        if (OrderStatus.CANCELLED.equals(status) && !PaymentStatus.PAID.equals(order.getPaymentStatus())) {
            order.setPaymentStatus(PaymentStatus.CANCELLED);
        }

        if (OrderStatus.REFUNDED.equals(status)) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }
    }

    private Session createCheckoutSession(Order order, BigDecimal total) throws Exception {
        Stripe.apiKey = stripeSecretKey;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/orders/" + order.getId())
                .setCancelUrl(frontendUrl + "/orders/" + order.getId() + "?payment=cancelled")
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

    private String getToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing token");
        }
        return authHeader.substring(7);
    }
}

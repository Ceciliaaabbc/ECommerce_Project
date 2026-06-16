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
import java.util.List;

@Service
public class OrderService {

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

        Stripe.apiKey = stripeSecretKey;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/orders")
                .setCancelUrl(frontendUrl + "/cart?payment=cancelled&orderId=" + savedOrder.getId())
                .setClientReferenceId(savedOrder.getId().toString())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(toCents(total))
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Order #" + savedOrder.getId())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        Session session = Session.create(params);
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

    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

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

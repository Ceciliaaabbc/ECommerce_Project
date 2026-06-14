package com.ecommerce.order;

import com.ecommerce.cart.CartItem;
import com.ecommerce.cart.CartItemRepository;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.security.JwtUtil;
import org.springframework.web.bind.annotation.*;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${frontend.url}")
    private String frontendUrl;

    public OrderController(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository, 
            JwtUtil jwtUtil
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;;
    }


    @PostMapping("/checkout")
    public CheckoutResponse checkout(@RequestHeader("Authorization") String authHeader) throws Exception {
        String token = authHeader.substring(7);
        String userEmail = jwtUtil.getEmailFromToken(token);

        List<CartItem> cartItems = cartItemRepository.findByUserEmail(userEmail);

        if (cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        double total = 0;

        for (CartItem item : cartItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (product.getStock() < item.getQuantity()) {
                throw new RuntimeException("Not enough stock for product: " + product.getTitle());
            }

            total += item.getPrice() * item.getQuantity();
        }

        Order order = new Order(userEmail, total, "PENDING_PAYMENT");
        Order savedOrder = orderRepository.save(order);

        for (CartItem item : cartItems) {
            OrderItem orderItem = new OrderItem(
                    savedOrder.getId(),
                    item.getProductId(),
                    item.getTitle(),
                    item.getPrice(),
                    item.getQuantity()
            );

            orderItemRepository.save(orderItem);
        }


        Stripe.apiKey = stripeSecretKey;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/orders")
                .setCancelUrl(frontendUrl + "/cart?payment=cancelled")
                .setClientReferenceId(savedOrder.getId().toString())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(Math.round(total * 100))
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

    @GetMapping
    public List<Order> getOrders(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);

        String userEmail = jwtUtil.getEmailFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        if ("ADMIN".equals(role)) {
            return orderRepository.findAll();
        }

        return orderRepository.findByUserEmail(userEmail);
    }



    @GetMapping("/{orderId}/items")
    public List<OrderItem> getOrderItems(@PathVariable Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    @PutMapping("/{orderId}/status")
    public Order updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam String status
    ) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(status);

        return orderRepository.save(order);
    }
}
package com.ecommerce.payment;

import com.ecommerce.cart.CartItemRepository;
import com.ecommerce.order.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final CartItemRepository cartItemRepository;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public PaymentService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            InventoryService inventoryService,
            CartItemRepository cartItemRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryService = inventoryService;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional
    public ResponseEntity<String> handleStripeWebhook(String payload, String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            JsonObject object = JsonParser.parseString(payload)
                    .getAsJsonObject()
                    .getAsJsonObject("data")
                    .getAsJsonObject("object");

            String eventType = event.getType();

            if ("checkout.session.completed".equals(eventType)) {
                return completeCheckoutSession(object);
            }

            if ("checkout.session.expired".equals(eventType)) {
                return expireCheckoutSession(object);
            }

            if ("payment_intent.payment_failed".equals(eventType)) {
                return ResponseEntity.ok("Payment failed event received");
            }

            return ResponseEntity.ok("Event ignored: " + eventType);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Webhook failed: " + e.getMessage());
        }
    }

    private ResponseEntity<String> completeCheckoutSession(JsonObject object) {
        String sessionId = object.get("id").getAsString();
        String paymentStatus = object.get("payment_status").getAsString();

        if (!"paid".equals(paymentStatus)) {
            return ResponseEntity.ok("Payment not paid yet");
        }

        Order order = getOrderFromClientReference(object);

        if (PaymentStatus.PAID.equals(order.getPaymentStatus())) {
            return ResponseEntity.ok("Order already paid");
        }

        if (PaymentStatus.EXPIRED.equals(order.getPaymentStatus()) || OrderStatus.CANCELLED.equals(order.getStatus())) {
            return ResponseEntity.ok("Order already expired, ignore paid event: " + order.getId());
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        for (OrderItem item : orderItems) {
            inventoryService.deductStock(item.getProductId(), item.getQuantity());
        }

        order.setStatus(OrderStatus.PROCESSING);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStripeSessionId(sessionId);
        orderRepository.save(order);

        cartItemRepository.deleteByUserEmail(order.getUserEmail());
        return ResponseEntity.ok("Order paid");
    }

    private ResponseEntity<String> expireCheckoutSession(JsonObject object) {
        String sessionId = object.get("id").getAsString();
        Order order = getOrderFromClientReference(object);

        if (PaymentStatus.PAID.equals(order.getPaymentStatus())) {
            return ResponseEntity.ok("Order already paid, ignore expired event: " + order.getId());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.EXPIRED);
        order.setStripeSessionId(sessionId);
        orderRepository.save(order);

        return ResponseEntity.ok("Order expired and cancelled: " + order.getId());
    }

    private Order getOrderFromClientReference(JsonObject object) {
        Long orderId = Long.valueOf(object.get("client_reference_id").getAsString());
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
    }
}

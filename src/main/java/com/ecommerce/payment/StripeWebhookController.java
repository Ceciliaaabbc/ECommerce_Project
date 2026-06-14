package com.ecommerce.payment;

import com.ecommerce.order.Order;
import com.ecommerce.order.OrderItem;
import com.ecommerce.order.OrderItemRepository;
import com.ecommerce.order.OrderRepository;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class StripeWebhookController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripeWebhookController(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            if (!"checkout.session.completed".equals(event.getType())) {
                return ResponseEntity.ok("Event ignored: " + event.getType());
            }

            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            JsonObject sessionObject = root
                    .getAsJsonObject("data")
                    .getAsJsonObject("object");

            String sessionId = sessionObject.get("id").getAsString();
            String clientReferenceId = sessionObject.get("client_reference_id").getAsString();
            String paymentStatus = sessionObject.get("payment_status").getAsString();

            if (!"paid".equals(paymentStatus)) {
                return ResponseEntity.ok("Payment not paid yet: " + paymentStatus);
            }

            Long orderId = Long.valueOf(clientReferenceId);

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

            if ("PAID".equals(order.getPaymentStatus())) {
                return ResponseEntity.ok("Order already paid: " + orderId);
            }

            List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

            for (OrderItem item : orderItems) {
                Product product = productRepository.findById(item.getProductId())
                        .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));

                if (product.getStock() < item.getQuantity()) {
                    throw new RuntimeException("Not enough stock for product: " + product.getTitle());
                }

                product.setStock(product.getStock() - item.getQuantity());
                productRepository.save(product);
            }

            order.setStatus("PAID");
            order.setPaymentStatus("PAID");
            order.setStripeSessionId(sessionId);

            orderRepository.save(order);

            return ResponseEntity.ok("Order paid and stock reduced: " + orderId);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Webhook failed: " + e.getMessage());
        }
    }
}
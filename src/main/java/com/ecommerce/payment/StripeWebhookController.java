package com.ecommerce.payment;

import com.ecommerce.order.Order;
import com.ecommerce.order.OrderRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class StripeWebhookController {

    private final OrderRepository orderRepository;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripeWebhookController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
        System.out.println("StripeWebhookController loaded - JSON version");
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            System.out.println("Stripe webhook event type: " + event.getType());

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

            System.out.println("Webhook sessionId: " + sessionId);
            System.out.println("Webhook clientReferenceId: " + clientReferenceId);
            System.out.println("Webhook paymentStatus: " + paymentStatus);

            if (!"paid".equals(paymentStatus)) {
                return ResponseEntity.ok("Payment not paid yet: " + paymentStatus);
            }

            Long orderId = Long.valueOf(clientReferenceId);

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

            order.setStatus("PAID");
            order.setPaymentStatus("PAID");
            order.setStripeSessionId(sessionId);

            orderRepository.save(order);

            System.out.println("Order updated to PAID: " + orderId);

            return ResponseEntity.ok("Order updated to PAID: " + orderId);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Webhook failed: " + e.getMessage());
        }
    }
}
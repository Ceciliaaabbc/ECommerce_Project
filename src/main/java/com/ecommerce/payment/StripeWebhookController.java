package com.ecommerce.payment;

import com.ecommerce.order.Order;
import com.ecommerce.order.OrderRepository;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
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
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Webhook signature verification failed");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElse(null);

            if (session == null) {
                System.out.println("Stripe session is null");
                return ResponseEntity.ok("Session is null");
            }

            System.out.println("Webhook session id: " + session.getId());
            System.out.println("Webhook client reference id: " + session.getClientReferenceId());
            System.out.println("Webhook payment status: " + session.getPaymentStatus());

            Long orderId = Long.valueOf(session.getClientReferenceId());

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

            order.setStatus("PAID");
            order.setPaymentStatus("PAID");
            order.setStripeSessionId(session.getId());

            orderRepository.save(order);

            System.out.println("Order updated to PAID: " + order.getId());
        }

        return ResponseEntity.ok("Webhook received");
    }
}
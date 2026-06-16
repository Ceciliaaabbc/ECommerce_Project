package com.ecommerce.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserEmail(String userEmail);
    Optional<Order> findByStripeSessionId(String stripeSessionId);
    List<Order> findByStatusAndPaymentStatusAndCreatedAtBefore(
            OrderStatus status,
            PaymentStatus paymentStatus,
            LocalDateTime createdAt
    );
}

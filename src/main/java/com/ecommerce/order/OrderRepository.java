package com.ecommerce.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    List<Order> findByUserEmail(String userEmail);
    Optional<Order> findByStripeSessionId(String stripeSessionId);
    List<Order> findByStatusAndPaymentStatusAndCreatedAtBefore(
            OrderStatus status,
            PaymentStatus paymentStatus,
            LocalDateTime createdAt
    );
}

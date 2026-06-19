package com.ecommerce.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserEmail(String userEmail);

    Optional<CartItem> findByUserEmailAndProductId(String userEmail, Long productId);

    Optional<CartItem> findByUserEmailAndProductIdAndVariantId(String userEmail, Long productId, Long variantId);

    List<CartItem> findByUserEmailAndProductIdAndVariantIdIsNull(String userEmail, Long productId);

    void deleteByUserEmail(String userEmail);
}
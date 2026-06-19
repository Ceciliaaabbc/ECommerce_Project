package com.ecommerce.cart;

import org.springframework.web.bind.annotation.*;
import com.ecommerce.security.JwtUtil;
import com.ecommerce.product.ProductVariant;
import com.ecommerce.product.ProductVariantRepository;
import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final JwtUtil jwtUtil;

    public CartController(
            CartItemRepository cartItemRepository,
            ProductVariantRepository productVariantRepository,
            JwtUtil jwtUtil
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public List<CartItem> getCartItems(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        String userEmail = jwtUtil.getEmailFromToken(token);

        return cartItemRepository.findByUserEmail(userEmail);
    }

    @PostMapping
    public CartItem addToCart(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CartItem cartItem
    ) {
        String token = authHeader.substring(7);
        String userEmail = jwtUtil.getEmailFromToken(token);

        cartItem.setUserEmail(userEmail);

        hydrateVariantDetails(cartItem);

        CartItem existingItem = findExistingCartItem(userEmail, cartItem);

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + 1);
            return cartItemRepository.save(existingItem);
        }

        cartItem.setQuantity(1);
        return cartItemRepository.save(cartItem);
    }

    private CartItem findExistingCartItem(String userEmail, CartItem cartItem) {
        if (cartItem.getVariantId() != null) {
            return cartItemRepository
                    .findByUserEmailAndProductIdAndVariantId(userEmail, cartItem.getProductId(), cartItem.getVariantId())
                    .orElse(null);
        }

        return cartItemRepository
                .findByUserEmailAndProductIdAndVariantIdIsNull(userEmail, cartItem.getProductId())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void hydrateVariantDetails(CartItem cartItem) {
        if (cartItem.getVariantId() == null) {
            return;
        }

        ProductVariant variant = productVariantRepository.findById(cartItem.getVariantId())
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        cartItem.setSku(variant.getSku());
        cartItem.setVariantName(formatVariantName(variant));
        if (variant.getPrice() != null) {
            cartItem.setPrice(variant.getPrice());
        }
    }

    private String formatVariantName(ProductVariant variant) {
        if (variant.getOptionName() == null || variant.getOptionName().isBlank()) {
            return variant.getOptionValue();
        }
        if (variant.getOptionValue() == null || variant.getOptionValue().isBlank()) {
            return variant.getOptionName();
        }
        return variant.getOptionName() + ": " + variant.getOptionValue();
    }

    @PutMapping("/{id}")
    public CartItem updateQuantity(@PathVariable Long id, @RequestParam Integer quantity) {
        CartItem cartItem = cartItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        cartItem.setQuantity(quantity);

        return cartItemRepository.save(cartItem);
    }

    @DeleteMapping("/{id}")
    public String removeFromCart(@PathVariable Long id) {
        cartItemRepository.deleteById(id);
        return "Cart item removed";
    }
}
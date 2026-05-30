package com.ecommerce.cart;

import org.springframework.web.bind.annotation.*;
import com.ecommerce.security.JwtUtil;
import java.util.List;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private final CartItemRepository cartItemRepository;
    private final JwtUtil jwtUtil;

    public CartController(CartItemRepository cartItemRepository, JwtUtil jwtUtil) {
        this.cartItemRepository = cartItemRepository;
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

        CartItem existingItem = cartItemRepository
                .findByUserEmailAndProductId(userEmail, cartItem.getProductId())
                .orElse(null);

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + 1);
            return cartItemRepository.save(existingItem);
        }

        cartItem.setQuantity(1);
        return cartItemRepository.save(cartItem);
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
package com.ecommerce.cart;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private final CartItemRepository cartItemRepository;

    public CartController(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    @GetMapping
    public List<CartItem> getCartItems(@RequestParam String userEmail) {
        return cartItemRepository.findByUserEmail(userEmail);
    }

    @PostMapping
    public CartItem addToCart(@RequestBody CartItem cartItem) {
        CartItem existingItem = cartItemRepository
                .findByUserEmailAndProductId(cartItem.getUserEmail(), cartItem.getProductId())
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
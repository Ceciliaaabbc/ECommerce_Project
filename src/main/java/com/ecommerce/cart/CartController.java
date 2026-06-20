package com.ecommerce.cart;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.ecommerce.order.InventoryService;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.security.JwtUtil;
import com.ecommerce.product.ProductVariant;
import com.ecommerce.product.ProductVariantRepository;
import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryService inventoryService;
    private final JwtUtil jwtUtil;

    public CartController(
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            InventoryService inventoryService,
            JwtUtil jwtUtil
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryService = inventoryService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public List<CartItem> getCartItems(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        String userEmail = jwtUtil.getEmailFromToken(token);

        return cartItemRepository.findByUserEmail(userEmail);
    }

    @PostMapping
    @Transactional
    public CartItem addToCart(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CartItem cartItem
    ) {
        String userEmail = getEmail(authHeader);
        return addOrIncrementCartItem(userEmail, cartItem);
    }

    private CartItem addOrIncrementCartItem(String userEmail, CartItem cartItem) {
        cartItem.setUserEmail(userEmail);

        hydrateProductAndVariantDetails(cartItem);
        int requestedQuantity = sanitizeQuantity(cartItem.getQuantity());

        CartItem existingItem = findExistingCartItem(userEmail, cartItem);

        if (existingItem != null) {
            int nextQuantity = existingItem.getQuantity() + requestedQuantity;
            inventoryService.requireEnoughStock(existingItem.getProductId(), existingItem.getVariantId(), nextQuantity);
            existingItem.setQuantity(nextQuantity);
            return cartItemRepository.save(existingItem);
        }

        inventoryService.requireEnoughStock(cartItem.getProductId(), cartItem.getVariantId(), requestedQuantity);
        cartItem.setQuantity(requestedQuantity);
        return cartItemRepository.save(cartItem);
    }

    private String getEmail(String authHeader) {
        String token = authHeader.substring(7);
        return jwtUtil.getEmailFromToken(token);
    }

    private int sanitizeQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            return 1;
        }
        return quantity;
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

    private void hydrateProductAndVariantDetails(CartItem cartItem) {
        Product product = productRepository.findById(cartItem.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        cartItem.setTitle(product.getTitle());
        cartItem.setPrice(product.getPrice());

        if (cartItem.getVariantId() == null) {
            return;
        }

        ProductVariant variant = productVariantRepository.findById(cartItem.getVariantId())
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        if (variant.getProduct() == null || !product.getId().equals(variant.getProduct().getId())) {
            throw new RuntimeException("Product variant does not belong to product");
        }

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
    @Transactional
    public CartItem updateQuantity(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestParam Integer quantity
    ) {
        String userEmail = getEmail(authHeader);
        CartItem cartItem = cartItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        requireCartOwner(cartItem, userEmail);
        int nextQuantity = sanitizeQuantity(quantity);
        inventoryService.requireEnoughStock(cartItem.getProductId(), cartItem.getVariantId(), nextQuantity);
        cartItem.setQuantity(nextQuantity);

        return cartItemRepository.save(cartItem);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public String removeFromCart(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id
    ) {
        String userEmail = getEmail(authHeader);
        CartItem cartItem = cartItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        requireCartOwner(cartItem, userEmail);
        cartItemRepository.delete(cartItem);
        return "Cart item removed";
    }

    private void requireCartOwner(CartItem cartItem, String userEmail) {
        if (!userEmail.equals(cartItem.getUserEmail())) {
            throw new RuntimeException("You cannot change this cart item");
        }
    }
}

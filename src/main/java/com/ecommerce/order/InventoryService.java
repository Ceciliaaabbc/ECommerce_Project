package com.ecommerce.order;

import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product requireEnoughStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getStock() < quantity) {
            throw new RuntimeException("Not enough stock for product: " + product.getTitle());
        }

        return product;
    }

    public void deductStock(Long productId, int quantity) {
        Product product = requireEnoughStock(productId, quantity);
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
    }
}

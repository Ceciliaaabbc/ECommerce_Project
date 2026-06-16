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

        if (getAvailableStock(product) < quantity) {
            throw new RuntimeException("Not enough stock for product: " + product.getTitle());
        }

        return product;
    }

    public void reserveStock(Long productId, int quantity) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (getAvailableStock(product) < quantity) {
            throw new RuntimeException("Not enough stock for product: " + product.getTitle());
        }

        product.setReservedStock(product.getReservedStock() + quantity);
        productRepository.save(product);
    }

    public void releaseReservedStock(Long productId, int quantity) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setReservedStock(Math.max(0, product.getReservedStock() - quantity));
        productRepository.save(product);
    }

    public void confirmReservedStock(Long productId, int quantity) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getReservedStock() < quantity) {
            throw new RuntimeException("Reserved stock is not enough for product: " + product.getTitle());
        }

        product.setReservedStock(product.getReservedStock() - quantity);
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
    }

    public void deductStock(Long productId, int quantity) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (getAvailableStock(product) < quantity) {
            throw new RuntimeException("Not enough stock for product: " + product.getTitle());
        }

        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
    }

    private int getAvailableStock(Product product) {
        return product.getAvailableStock();
    }
}

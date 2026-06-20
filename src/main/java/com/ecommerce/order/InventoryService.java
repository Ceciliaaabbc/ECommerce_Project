package com.ecommerce.order;

import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.product.ProductVariant;
import com.ecommerce.product.ProductVariantRepository;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    public InventoryService(ProductRepository productRepository, ProductVariantRepository productVariantRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
    }

    public Product requireEnoughStock(Long productId, int quantity) {
        return requireEnoughStock(productId, null, quantity);
    }

    public Product requireEnoughStock(Long productId, Long variantId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (variantId != null) {
            ProductVariant variant = productVariantRepository.findById(variantId)
                    .orElseThrow(() -> new RuntimeException("Product variant not found"));

            if (!productId.equals(variant.getProduct().getId())) {
                throw new RuntimeException("Product variant does not belong to product");
            }

            if (variant.getAvailableStock() < quantity) {
                throw new RuntimeException("Not enough stock for SKU: " + variant.getSku());
            }

            return product;
        }

        if (getAvailableStock(product) < quantity) {
            throw new RuntimeException("Not enough stock for product: " + product.getTitle());
        }

        return product;
    }

    public void reserveStock(Long productId, int quantity) {
        reserveStock(productId, null, quantity);
    }

    public void reserveStock(Long productId, Long variantId, int quantity) {
        if (variantId != null) {
            reserveVariantStock(productId, variantId, quantity);
            return;
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (getAvailableStock(product) < quantity) {
            throw new RuntimeException("Not enough stock for product: " + product.getTitle());
        }

        product.setReservedStock(product.getReservedStock() + quantity);
        productRepository.save(product);
    }

    public void releaseReservedStock(Long productId, int quantity) {
        releaseReservedStock(productId, null, quantity);
    }

    public void releaseReservedStock(Long productId, Long variantId, int quantity) {
        if (variantId != null) {
            releaseReservedVariantStock(productId, variantId, quantity);
            return;
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setReservedStock(Math.max(0, product.getReservedStock() - quantity));
        productRepository.save(product);
    }

    public void confirmReservedStock(Long productId, int quantity) {
        confirmReservedStock(productId, null, quantity);
    }

    public void confirmReservedStock(Long productId, Long variantId, int quantity) {
        if (variantId != null) {
            confirmReservedVariantStock(productId, variantId, quantity);
            return;
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getReservedStock() < quantity) {
            throw new RuntimeException("Reserved stock is not enough for product: " + product.getTitle());
        }

        product.setReservedStock(product.getReservedStock() - quantity);
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
    }

    private void reserveVariantStock(Long productId, Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        validateVariantProduct(productId, variant);

        if (variant.getAvailableStock() < quantity) {
            throw new RuntimeException("Not enough stock for SKU: " + variant.getSku());
        }

        variant.setReservedStock(variant.getReservedStock() + quantity);
        productVariantRepository.save(variant);
    }

    private void releaseReservedVariantStock(Long productId, Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        validateVariantProduct(productId, variant);
        variant.setReservedStock(Math.max(0, variant.getReservedStock() - quantity));
        productVariantRepository.save(variant);
    }

    private void confirmReservedVariantStock(Long productId, Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        validateVariantProduct(productId, variant);

        if (variant.getReservedStock() < quantity) {
            throw new RuntimeException("Reserved stock is not enough for SKU: " + variant.getSku());
        }

        variant.setReservedStock(variant.getReservedStock() - quantity);
        variant.setStock(variant.getStock() - quantity);
        productVariantRepository.save(variant);
    }

    private void validateVariantProduct(Long productId, ProductVariant variant) {
        if (variant.getProduct() == null || !productId.equals(variant.getProduct().getId())) {
            throw new RuntimeException("Product variant does not belong to product");
        }
    }

    public void deductStock(Long productId, int quantity) {
        deductStock(productId, null, quantity);
    }

    public void deductStock(Long productId, Long variantId, int quantity) {
        if (variantId != null) {
            deductVariantStock(productId, variantId, quantity);
            return;
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (getAvailableStock(product) < quantity) {
            throw new RuntimeException("Not enough stock for product: " + product.getTitle());
        }

        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
    }

    private void deductVariantStock(Long productId, Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        validateVariantProduct(productId, variant);

        if (variant.getAvailableStock() < quantity) {
            throw new RuntimeException("Not enough stock for SKU: " + variant.getSku());
        }

        variant.setStock(variant.getStock() - quantity);
        productVariantRepository.save(variant);
    }

    private int getAvailableStock(Product product) {
        return product.getAvailableStock();
    }
}

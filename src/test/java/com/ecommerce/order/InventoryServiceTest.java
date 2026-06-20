package com.ecommerce.order;

import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.product.ProductVariant;
import com.ecommerce.product.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductVariantRepository productVariantRepository = mock(ProductVariantRepository.class);
    private final InventoryService inventoryService = new InventoryService(productRepository, productVariantRepository);

    @Test
    void reserveStock_shouldIncreaseReservedStockWhenAvailable() {
        Product product = product("Phone", 5, 1);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        inventoryService.reserveStock(1L, 3);

        assertThat(product.getStock()).isEqualTo(5);
        assertThat(product.getReservedStock()).isEqualTo(4);
        assertThat(product.getAvailableStock()).isEqualTo(1);
        verify(productRepository).save(product);
    }

    @Test
    void reserveStock_shouldRejectWhenAvailableStockIsNotEnough() {
        Product product = product("Phone", 5, 4);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> inventoryService.reserveStock(1L, 2))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not enough stock");
    }

    @Test
    void confirmReservedStock_shouldConvertReservationIntoDeductedStock() {
        Product product = product("Phone", 5, 3);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        inventoryService.confirmReservedStock(1L, 2);

        assertThat(product.getStock()).isEqualTo(3);
        assertThat(product.getReservedStock()).isEqualTo(1);
        verify(productRepository).save(product);
    }

    @Test
    void releaseReservedStock_shouldReduceReservedStockWithoutGoingNegative() {
        Product product = product("Phone", 5, 1);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        inventoryService.releaseReservedStock(1L, 3);

        assertThat(product.getStock()).isEqualTo(5);
        assertThat(product.getReservedStock()).isZero();
        verify(productRepository).save(product);
    }

    @Test
    void reserveStock_shouldReserveVariantStockWhenVariantIdExists() {
        Product product = product(1L, "Shoes", 10, 0);
        ProductVariant variant = variant(product, "SHOE-9", 4, 1);
        when(productVariantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(variant));

        inventoryService.reserveStock(1L, 20L, 2);

        assertThat(variant.getStock()).isEqualTo(4);
        assertThat(variant.getReservedStock()).isEqualTo(3);
        assertThat(variant.getAvailableStock()).isEqualTo(1);
        verify(productVariantRepository).save(variant);
    }

    @Test
    void confirmReservedStock_shouldDeductVariantStock() {
        Product product = product(1L, "Shoes", 10, 0);
        ProductVariant variant = variant(product, "SHOE-9", 4, 3);
        when(productVariantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(variant));

        inventoryService.confirmReservedStock(1L, 20L, 2);

        assertThat(variant.getStock()).isEqualTo(2);
        assertThat(variant.getReservedStock()).isEqualTo(1);
        verify(productVariantRepository).save(variant);
    }

    @Test
    void releaseReservedStock_shouldReleaseVariantReservation() {
        Product product = product(1L, "Shoes", 10, 0);
        ProductVariant variant = variant(product, "SHOE-9", 4, 3);
        when(productVariantRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(variant));

        inventoryService.releaseReservedStock(1L, 20L, 2);

        assertThat(variant.getStock()).isEqualTo(4);
        assertThat(variant.getReservedStock()).isEqualTo(1);
        verify(productVariantRepository).save(variant);
    }

    private Product product(String title, int stock, int reservedStock) {
        return product(null, title, stock, reservedStock);
    }

    private Product product(Long id, String title, int stock, int reservedStock) {
        Product product = new Product();
        if (id != null) {
            ReflectionTestUtils.setField(product, "id", id);
        }
        product.setTitle(title);
        product.setStock(stock);
        product.setReservedStock(reservedStock);
        product.setPrice(BigDecimal.TEN);
        return product;
    }

    private ProductVariant variant(Product product, String sku, int stock, int reservedStock) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setStock(stock);
        variant.setReservedStock(reservedStock);
        variant.setPrice(BigDecimal.TEN);
        return variant;
    }
}

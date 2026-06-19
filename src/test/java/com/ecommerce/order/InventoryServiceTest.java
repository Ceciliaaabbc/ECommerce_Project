package com.ecommerce.order;

import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final InventoryService inventoryService = new InventoryService(productRepository);

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

    private Product product(String title, int stock, int reservedStock) {
        Product product = new Product();
        product.setTitle(title);
        product.setStock(stock);
        product.setReservedStock(reservedStock);
        return product;
    }
}

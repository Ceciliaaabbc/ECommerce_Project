package com.ecommerce.order;

import com.ecommerce.address.Address;
import com.ecommerce.address.AddressRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderAddressSnapshotServiceTest {

    private final AddressRepository addressRepository = mock(AddressRepository.class);
    private final OrderAddressSnapshotService service = new OrderAddressSnapshotService(addressRepository);

    @Test
    void applyShippingAddressSnapshot_shouldCopyAddressFieldsIntoOrder() throws Exception {
        Address address = new Address();
        setAddressId(address, 10L);
        address.setUserEmail("buyer@example.com");
        address.setRecipientName("Jane Buyer");
        address.setPhone("5551234");
        address.setCountry("US");
        address.setProvince("CA");
        address.setCity("San Jose");
        address.setStreet("1 Market Street");
        address.setPostalCode("95113");
        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));

        Order order = new Order("buyer@example.com", BigDecimal.TEN, OrderStatus.PENDING_PAYMENT);
        service.applyShippingAddressSnapshot(order, 10L, "buyer@example.com");

        assertThat(order.getShippingAddressId()).isEqualTo(10L);
        assertThat(order.getShippingRecipientName()).isEqualTo("Jane Buyer");
        assertThat(order.getShippingPhone()).isEqualTo("5551234");
        assertThat(order.getShippingCountry()).isEqualTo("US");
        assertThat(order.getShippingProvince()).isEqualTo("CA");
        assertThat(order.getShippingCity()).isEqualTo("San Jose");
        assertThat(order.getShippingStreet()).isEqualTo("1 Market Street");
        assertThat(order.getShippingPostalCode()).isEqualTo("95113");
    }

    @Test
    void applyShippingAddressSnapshot_shouldRejectAnotherUsersAddress() {
        Address address = new Address();
        address.setUserEmail("other@example.com");
        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));

        Order order = new Order("buyer@example.com", BigDecimal.TEN, OrderStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> service.applyShippingAddressSnapshot(order, 10L, "buyer@example.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("You cannot use this shipping address");
    }

    @Test
    void applyShippingAddressSnapshot_shouldRequireAddressId() {
        Order order = new Order("buyer@example.com", BigDecimal.TEN, OrderStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> service.applyShippingAddressSnapshot(order, null, "buyer@example.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Shipping address is required");
    }

    private void setAddressId(Address address, Long id) throws Exception {
        java.lang.reflect.Field idField = Address.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(address, id);
    }
}

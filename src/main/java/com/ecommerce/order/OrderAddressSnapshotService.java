package com.ecommerce.order;

import com.ecommerce.address.Address;
import com.ecommerce.address.AddressRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderAddressSnapshotService {

    private final AddressRepository addressRepository;

    public OrderAddressSnapshotService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    public void applyShippingAddressSnapshot(Order order, Long shippingAddressId, String userEmail) {
        if (shippingAddressId == null) {
            throw new RuntimeException("Shipping address is required");
        }

        Address address = addressRepository.findById(shippingAddressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("You cannot use this shipping address");
        }

        order.setShippingAddressId(address.getId());
        order.setShippingRecipientName(address.getRecipientName());
        order.setShippingPhone(address.getPhone());
        order.setShippingCountry(address.getCountry());
        order.setShippingProvince(address.getProvince());
        order.setShippingCity(address.getCity());
        order.setShippingStreet(address.getStreet());
        order.setShippingPostalCode(address.getPostalCode());
    }
}

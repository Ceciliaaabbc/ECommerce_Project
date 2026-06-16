package com.ecommerce.address;

import com.ecommerce.security.JwtUtil;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressRepository addressRepository;
    private final JwtUtil jwtUtil;

    public AddressController(AddressRepository addressRepository, JwtUtil jwtUtil) {
        this.addressRepository = addressRepository;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public List<Address> getAddresses(@RequestHeader("Authorization") String authHeader) {
        return addressRepository.findByUserEmail(getEmail(authHeader));
    }

    @GetMapping("/{id}")
    public Address getAddress(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        String userEmail = getEmail(authHeader);
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("You cannot view this address");
        }

        return address;
    }

    @PostMapping
    @Transactional
    public Address createAddress(@RequestHeader("Authorization") String authHeader, @RequestBody Address address) {
        String userEmail = getEmail(authHeader);
        address.setUserEmail(userEmail);

        List<Address> existingAddresses = addressRepository.findByUserEmail(userEmail);
        if (address.isDefaultAddress() || existingAddresses.isEmpty()) {
            clearDefaultAddress(existingAddresses);
            address.setDefaultAddress(true);
        }

        return addressRepository.save(address);
    }

    @PutMapping("/{id}/default")
    @Transactional
    public Address setDefaultAddress(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        String userEmail = getEmail(authHeader);
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("You cannot update this address");
        }

        clearDefaultAddress(addressRepository.findByUserEmail(userEmail));
        address.setDefaultAddress(true);
        return addressRepository.save(address);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public String deleteAddress(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        String userEmail = getEmail(authHeader);
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("You cannot delete this address");
        }

        boolean deletedDefaultAddress = address.isDefaultAddress();
        addressRepository.delete(address);

        if (deletedDefaultAddress) {
            List<Address> remainingAddresses = addressRepository.findByUserEmail(userEmail);
            if (!remainingAddresses.isEmpty()) {
                Address nextDefaultAddress = remainingAddresses.get(0);
                nextDefaultAddress.setDefaultAddress(true);
                addressRepository.save(nextDefaultAddress);
            }
        }

        return "Address deleted";
    }

    private void clearDefaultAddress(List<Address> addresses) {
        for (Address existingAddress : addresses) {
            if (existingAddress.isDefaultAddress()) {
                existingAddress.setDefaultAddress(false);
                addressRepository.save(existingAddress);
            }
        }
    }

    private String getEmail(String authHeader) {
        return jwtUtil.getEmailFromToken(authHeader.substring(7));
    }
}

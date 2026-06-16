package com.ecommerce.address;

import com.ecommerce.security.JwtUtil;
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

    @PostMapping
    public Address createAddress(@RequestHeader("Authorization") String authHeader, @RequestBody Address address) {
        address.setUserEmail(getEmail(authHeader));
        return addressRepository.save(address);
    }

    @DeleteMapping("/{id}")
    public String deleteAddress(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        String userEmail = getEmail(authHeader);
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("You cannot delete this address");
        }

        addressRepository.delete(address);
        return "Address deleted";
    }

    private String getEmail(String authHeader) {
        return jwtUtil.getEmailFromToken(authHeader.substring(7));
    }
}

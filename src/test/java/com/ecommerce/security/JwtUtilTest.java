package com.ecommerce.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    // test: generateToken(), getEmailFromToken(), getRoleFromToken(), validateToken()

    @Test
    void generateToken_shouldContainEmailAndRole() {
        String secret = "my-super-secret-key-my-super-secret-key-123456";
        JwtUtil jwtUtil = new JwtUtil(secret);

        String token = jwtUtil.generateToken("user@example.com", "USER");

        assertNotNull(token);
        assertEquals("user@example.com", jwtUtil.getEmailFromToken(token));
        assertEquals("USER", jwtUtil.getRoleFromToken(token));
    }

    @Test
    void generateToken_shouldWorkForAdminRole() {
        String secret = "my-super-secret-key-my-super-secret-key-123456";
        JwtUtil jwtUtil = new JwtUtil(secret);

        String token = jwtUtil.generateToken("admin@example.com", "ADMIN");

        assertNotNull(token);
        assertEquals("admin@example.com", jwtUtil.getEmailFromToken(token));
        assertEquals("ADMIN", jwtUtil.getRoleFromToken(token));
    }
}
package com.ecommerce.user;

public class LoginResponse {

    private String email;
    private String role;
    private String token;

    public LoginResponse(String email, String role, String token) {
        this.email = email;
        this.role = role;
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getToken() {
        return token;
    }
}
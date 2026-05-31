package com.ecommerce.user;

import com.ecommerce.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void registerUser_shouldReturnSuccess_whenEmailDoesNotExist() throws Exception {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");

        mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "test",
                                  "email": "test@example.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Register successful"));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void loginUser_shouldReturnToken_whenPasswordIsCorrect() throws Exception {
        User user = new User("test", "test@example.com", "encoded-password");
        user.setRole("USER");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateToken("test@example.com", "USER")).thenReturn("fake-jwt-token");

        mockMvc.perform(post("/api/users/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.token").value("fake-jwt-token"));
    }
}
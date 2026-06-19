package com.ecommerce.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.ecommerce.security.JwtUtil;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder,  JwtUtil jwtUtil  ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }
    @PostMapping("/register")
    public String registerUser(@RequestBody User user) {
        User existingUser = userRepository.findByEmail(user.getEmail())
                .orElse(null);

        if (existingUser != null) {
            return "Email already exists";
        }

        user.setRole("USER");

        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        userRepository.save(user);

        return "Register successful";
    }

    // 登录成功后返回用户email, role, token
    @PostMapping("/login")
    public LoginResponse loginUser(@RequestBody LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean passwordMatches = passwordEncoder.matches(
                loginRequest.getPassword(),
                user.getPassword()
        );

        if (!passwordMatches) {
            throw new RuntimeException("Wrong password");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());

        return new LoginResponse(user.getEmail(), user.getRole(), token);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public User getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // PUT /api/users/1/make-admin
    @PutMapping("/{id}/make-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public User makeAdmin(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setRole("ADMIN");

        return userRepository.save(user);
    }

}
package com.razorpay.buildathon.recon.controller;

import com.razorpay.buildathon.recon.model.UserAccount;
import com.razorpay.buildathon.recon.repository.UserAccountRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Production-grade REST Controller for User Registration, Authentication,
 * and Password Validation with persistent database storage.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountRepository userRepository;

    public AuthController(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void seedDefaultUsers() {
        if (!userRepository.existsByEmail("controller@razorpay.com")) {
            userRepository.save(new UserAccount(
                    "controller@razorpay.com",
                    hashPassword("password123"),
                    "Nilesh Kanti",
                    "Finance Controller"
            ));
        }
        if (!userRepository.existsByEmail("audit@finops.org")) {
            userRepository.save(new UserAccount(
                    "audit@finops.org",
                    hashPassword("password123"),
                    "Sarah Jenkins",
                    "Audit Analyst"
            ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email and password are required."));
        }

        String normalizedEmail = email.toLowerCase().trim();
        UserAccount user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials. No user found with this email."));
        }

        if (!user.getPasswordHash().equals(hashPassword(password))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials. Incorrect password."));
        }

        return ResponseEntity.ok(Map.of(
                "token", "jwt_token_" + System.currentTimeMillis(),
                "user", Map.of(
                        "email", user.getEmail(),
                        "name", user.getName(),
                        "role", user.getRole(),
                        "status", user.getStatus()
                ),
                "message", "Sign in successful"
        ));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        String name = body.get("name");

        if (email == null || email.isBlank() || !email.contains("@")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "A valid email address is required."));
        }

        if (password == null || password.length() < 6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Password must be at least 6 characters long."));
        }

        String normalizedEmail = email.toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "An account with this email address already exists. Please sign in instead."));
        }

        String displayName = name != null && !name.isBlank() ? name.trim() : "Finance Analyst";
        UserAccount newUser = new UserAccount(normalizedEmail, hashPassword(password), displayName, "Finance Controller");
        userRepository.save(newUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "token", "jwt_token_" + System.currentTimeMillis(),
                "user", Map.of(
                        "email", newUser.getEmail(),
                        "name", newUser.getName(),
                        "role", newUser.getRole(),
                        "status", newUser.getStatus()
                ),
                "message", "Account registered successfully"
        ));
    }

    private String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

package com.razorpay.buildathon.recon.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for User Authentication, Sign In, Sign Up, and Token Issue.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "controller@razorpay.com");
        String name = email.contains("@") ? email.split("@")[0] : "Finance Controller";
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);

        return ResponseEntity.ok(Map.of(
                "token", "jwt_recon_token_" + System.currentTimeMillis(),
                "user", Map.of(
                        "email", email,
                        "name", name,
                        "role", "Finance Controller",
                        "status", "ACTIVE"
                ),
                "message", "Authentication successful"
        ));
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "analyst@finops.org");
        String name = body.getOrDefault("name", "Finance Analyst");

        return ResponseEntity.ok(Map.of(
                "token", "jwt_recon_token_" + System.currentTimeMillis(),
                "user", Map.of(
                        "email", email,
                        "name", name,
                        "role", "Finance Controller",
                        "status", "ACTIVE"
                ),
                "message", "Account registered successfully"
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        return ResponseEntity.ok(Map.of(
                "email", "controller@razorpay.com",
                "name", "Nilesh Kanti",
                "role", "Finance Controller",
                "status", "ACTIVE"
        ));
    }
}

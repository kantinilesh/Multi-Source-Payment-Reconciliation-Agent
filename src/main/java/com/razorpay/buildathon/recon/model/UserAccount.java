package com.razorpay.buildathon.recon.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persistent user account entity for finance portal authentication.
 */
@Entity
@Table(name = "user_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_email", columnNames = "email")
})
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String role = "Finance Controller";

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UserAccount() {}

    public UserAccount(String email, String passwordHash, String name, String role) {
        this.email = email.toLowerCase().trim();
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role != null ? role : "Finance Controller";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email != null ? email.toLowerCase().trim() : null; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

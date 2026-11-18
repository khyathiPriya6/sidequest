package com.sidequest.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Persistence model only: never serialize this entity into an API response or session. */
@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(nullable = false, length = 30)
    private String username;
    @Column(name = "password_hash", nullable = false, length = 512)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() { }

    public static User create(String email, String username, String encodedPassword) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = normalizeEmail(email);
        user.username = Objects.requireNonNull(username).toLowerCase(Locale.ROOT);
        if (!user.username.matches("[a-z0-9_]{3,30}")) {
            throw new IllegalArgumentException("Invalid username");
        }
        if (encodedPassword == null || !encodedPassword.startsWith("{argon2}$argon2id$")
                || encodedPassword.length() > 512) {
            throw new IllegalArgumentException("An encoded Argon2id password is required");
        }
        user.passwordHash = encodedPassword;
        user.status = AccountStatus.ACTIVE;
        return user;
    }

    public static String normalizeEmail(String email) {
        String normalized = Objects.requireNonNull(email).strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 254) {
            throw new IllegalArgumentException("Invalid email length");
        }
        return normalized;
    }

    @PrePersist
    void created() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void updated() { updatedAt = Instant.now(); }

    public void disable() { status = AccountStatus.DISABLED; }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

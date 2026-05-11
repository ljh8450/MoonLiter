package com.moonliter.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuthProvider authProvider;

    @Column(nullable = false, length = 120)
    private String providerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(String email, AuthProvider authProvider, String providerUserId) {
        this.email = email;
        this.authProvider = authProvider;
        this.providerUserId = providerUserId;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

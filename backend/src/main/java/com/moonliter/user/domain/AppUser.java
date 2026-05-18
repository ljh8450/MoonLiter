package com.moonliter.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "app_users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_users_verified_phone_hash", columnNames = "verified_phone_hash")
    }
)
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

    @Column
    private Instant emailVerifiedAt;

    @Column(name = "verified_phone_e164", length = 32)
    private String verifiedPhoneE164;

    @Column(name = "verified_phone_hash", length = 128)
    private String verifiedPhoneHash;

    @Column
    private Instant phoneVerifiedAt;

    @Column(nullable = false)
    private int suspensionCount = 0;

    @Column
    private Instant suspendedUntil;

    @Column(nullable = false)
    private boolean permanentlySuspended = false;

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

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getVerifiedPhoneE164() {
        return verifiedPhoneE164;
    }

    public String getVerifiedPhoneHash() {
        return verifiedPhoneHash;
    }

    public Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public int getSuspensionCount() {
        return suspensionCount;
    }

    public Instant getSuspendedUntil() {
        return suspendedUntil;
    }

    public boolean isPermanentlySuspended() {
        return permanentlySuspended;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean canUseEmailVerifiedFeatures() {
        return authProvider != AuthProvider.EMAIL || emailVerifiedAt != null;
    }

    public boolean isSuspendedAt(Instant now) {
        if (status == UserStatus.WITHDRAWN) {
            return true;
        }
        if (permanentlySuspended) {
            return true;
        }
        return suspendedUntil != null && suspendedUntil.isAfter(now);
    }

    public void verifyEmail(Instant verifiedAt) {
        this.emailVerifiedAt = verifiedAt;
        touch(verifiedAt);
    }

    public void verifyPhone(String phoneE164, String phoneHash, Instant verifiedAt) {
        this.verifiedPhoneE164 = phoneE164;
        this.verifiedPhoneHash = phoneHash;
        this.phoneVerifiedAt = verifiedAt;
        touch(verifiedAt);
    }

    public int increaseSuspensionCount(Instant now) {
        this.suspensionCount += 1;
        touch(now);
        return suspensionCount;
    }

    public void suspendUntil(Instant suspendedUntil, Instant now) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedUntil = suspendedUntil;
        this.permanentlySuspended = false;
        touch(now);
    }

    public void suspendPermanently(Instant now) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedUntil = null;
        this.permanentlySuspended = true;
        touch(now);
    }

    public void clearSuspension(Instant now) {
        if (status == UserStatus.SUSPENDED) {
            this.status = UserStatus.ACTIVE;
        }
        this.suspendedUntil = null;
        this.permanentlySuspended = false;
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }
}

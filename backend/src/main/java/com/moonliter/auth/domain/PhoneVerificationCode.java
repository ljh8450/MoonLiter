package com.moonliter.auth.domain;

import com.moonliter.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "phone_verification_codes")
public class PhoneVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 32)
    private String phoneE164;

    @Column(nullable = false, length = 128)
    private String phoneHash;

    @Column(nullable = false, length = 120)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PhoneVerificationCode() {
    }

    public PhoneVerificationCode(
        AppUser user,
        String phoneE164,
        String phoneHash,
        String codeHash,
        Instant expiresAt
    ) {
        this.user = user;
        this.phoneE164 = phoneE164;
        this.phoneHash = phoneHash;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void increaseAttempts() {
        this.attempts += 1;
    }

    public void consume(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}

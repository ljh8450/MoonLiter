package com.moonliter.admin.domain;

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
@Table(name = "user_suspensions")
public class UserSuspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private AppUser admin;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private int suspensionCount;

    @Column(nullable = false)
    private Instant startsAt;

    @Column
    private Instant endsAt;

    @Column(nullable = false)
    private boolean permanent;

    @Column
    private Instant releasedAt;

    @Column(length = 500)
    private String releaseReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected UserSuspension() {
    }

    public UserSuspension(
        AppUser user,
        AppUser admin,
        String reason,
        int suspensionCount,
        Instant startsAt,
        Instant endsAt,
        boolean permanent
    ) {
        this.user = user;
        this.admin = admin;
        this.reason = reason;
        this.suspensionCount = suspensionCount;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.permanent = permanent;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public AppUser getAdmin() {
        return admin;
    }

    public String getReason() {
        return reason;
    }

    public int getSuspensionCount() {
        return suspensionCount;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void release(String reason, Instant releasedAt) {
        this.releaseReason = reason;
        this.releasedAt = releasedAt;
    }
}

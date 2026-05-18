package com.moonliter.notification.domain;

import com.moonliter.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_notifications")
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    private boolean read = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AppNotification() {
    }

    public AppNotification(AppUser user, NotificationType type, String title, String body) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.body = body;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markAsRead() {
        this.read = true;
    }
}

package com.moonliter.postcard.domain;

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
@Table(name = "postcards")
public class Postcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostcardStatus status = PostcardStatus.DELIVERABLE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Postcard() {
    }

    public Postcard(AppUser author, String content) {
        this.author = author;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public AppUser getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public PostcardStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

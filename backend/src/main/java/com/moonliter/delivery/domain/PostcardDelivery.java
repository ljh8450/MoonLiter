package com.moonliter.delivery.domain;

import com.moonliter.postcard.domain.Postcard;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "postcard_deliveries",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_postcard_deliveries_receiver_date",
            columnNames = {"receiver_id", "delivered_date"}
        )
    }
)
public class PostcardDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "postcard_id", nullable = false)
    private Postcard postcard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private AppUser receiver;

    @Column(nullable = false)
    private LocalDate deliveredDate;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PostcardDelivery() {
    }

    public PostcardDelivery(Postcard postcard, AppUser receiver, LocalDate deliveredDate) {
        this.postcard = postcard;
        this.receiver = receiver;
        this.deliveredDate = deliveredDate;
    }

    public Long getId() {
        return id;
    }

    public Postcard getPostcard() {
        return postcard;
    }

    public AppUser getReceiver() {
        return receiver;
    }

    public LocalDate getDeliveredDate() {
        return deliveredDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

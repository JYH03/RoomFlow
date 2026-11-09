package com.jyh03.roomflow.domain.reservationoffer;

import com.jyh03.roomflow.domain.waitingqueue.WaitingQueue;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_offers")
public class ReservationOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiting_queue_id", nullable = false)
    private WaitingQueue waitingQueue;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationOfferStatus status;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReservationOffer() {
    }

    public ReservationOffer(WaitingQueue waitingQueue, LocalDateTime expiresAt) {
        this.waitingQueue = waitingQueue;
        this.expiresAt = expiresAt;
        this.status = ReservationOfferStatus.PENDING;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void accept() {
        this.status = ReservationOfferStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void decline() {
        this.status = ReservationOfferStatus.DECLINED;
        this.respondedAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = ReservationOfferStatus.EXPIRED;
    }

    public Long getId() {
        return id;
    }

    public WaitingQueue getWaitingQueue() {
        return waitingQueue;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public ReservationOfferStatus getStatus() {
        return status;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

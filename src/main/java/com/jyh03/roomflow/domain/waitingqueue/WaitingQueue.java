package com.jyh03.roomflow.domain.waitingqueue;

import java.time.LocalDateTime;

import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.domain.user.User;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "waiting_queues", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "uk_waiting_queue_order", columnNames = {"room_id", "start_at", "queue_order"}))
public class WaitingQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "queue_order", nullable = false)
    private int queueOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitingQueueStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WaitingQueue() {
    }

    public WaitingQueue(
            User user,
            Room room,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int queueOrder) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }

        this.user = user;
        this.room = room;
        this.startAt = startAt;
        this.endAt = endAt;
        this.queueOrder = queueOrder;
        this.status = WaitingQueueStatus.WAITING;
    }

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = WaitingQueueStatus.CANCELED;
    }

    public void offer() {
        this.status = WaitingQueueStatus.OFFERED;
    }

    public void accept() {
        this.status = WaitingQueueStatus.ACCEPTED;
    }

    public void expire() {
        this.status = WaitingQueueStatus.EXPIRED;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Room getRoom() {
        return room;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public int getQueueOrder() {
        return queueOrder;
    }

    public WaitingQueueStatus getStatus() {
        return status;
    }
}

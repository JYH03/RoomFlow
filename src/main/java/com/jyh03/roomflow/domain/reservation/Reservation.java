package com.jyh03.roomflow.domain.reservation;

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
@Table(name = "reservations")
public class Reservation {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Reservation() {
    }

    public Reservation(User user, Room room, LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }

        this.user = user;
        this.room = room;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = ReservationStatus.CONFIRMED;
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
        this.status = ReservationStatus.CANCELED;
    }

    public void checkIn() {
        checkIn(LocalDateTime.now());
    }

    public void checkIn(LocalDateTime now) {
        if (status != ReservationStatus.CONFIRMED || checkedInAt != null) {
            throw new IllegalArgumentException("확정된 미입실 예약만 체크인할 수 있습니다.");
        }
        if (now.isBefore(startAt)) {
            throw new IllegalArgumentException("예약 시작 시각부터 체크인할 수 있습니다.");
        }
        if (!now.isBefore(getCheckInDeadline())) {
            throw new IllegalArgumentException("체크인 가능 시간이 지났습니다.");
        }
        this.status = ReservationStatus.CHECKED_IN;
        this.checkedInAt = now;
    }

    public LocalDateTime getCheckInDeadline() {
        // Late promotion receives its own grace period without extending the slot.
        LocalDateTime base = createdAt != null && createdAt.isAfter(startAt) ? createdAt : startAt;
        LocalDateTime deadline = base.plusMinutes(5);
        return deadline.isBefore(endAt) ? deadline : endAt;
    }

    public boolean markNoShow(LocalDateTime now) {
        if (status != ReservationStatus.CONFIRMED || checkedInAt != null
                || now.isBefore(getCheckInDeadline())) {
            return false;
        }
        this.status = ReservationStatus.NO_SHOW;
        return true;
    }

    public Long getId() {
        return id;
    }

    public boolean complete(LocalDateTime now) {
        if (status != ReservationStatus.CHECKED_IN || now.isBefore(endAt)) {
            return false;
        }
        status = ReservationStatus.COMPLETED;
        return true;
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

    public ReservationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }
}

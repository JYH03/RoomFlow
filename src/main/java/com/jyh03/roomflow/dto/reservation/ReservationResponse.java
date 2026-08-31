package com.jyh03.roomflow.dto.reservation;

import java.time.LocalDateTime;

import com.jyh03.roomflow.domain.reservation.Reservation;
import com.jyh03.roomflow.domain.reservation.ReservationStatus;

public record ReservationResponse(
        Long id,
        Long userId,
        Long roomId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        ReservationStatus status) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getUser().getId(),
                reservation.getRoom().getId(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                reservation.getStatus());
    }
}
package com.jyh03.roomflow.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.jyh03.roomflow.domain.reservation.Reservation;
import com.jyh03.roomflow.domain.reservation.ReservationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByRoomIdAndStartAtGreaterThanEqualAndStartAtLessThanAndStatusIn(
            Long roomId,
            LocalDateTime startOfDay,
            LocalDateTime nextDayStart,
            Collection<ReservationStatus> statuses
    );

    boolean existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
            Long roomId,
            LocalDateTime requestedEndAt,
            LocalDateTime requestedStartAt,
            Collection<ReservationStatus> statuses
    );

    boolean existsByUserIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
            Long userId,
            LocalDateTime requestedEndAt,
            LocalDateTime requestedStartAt,
            Collection<ReservationStatus> statuses
    );
}
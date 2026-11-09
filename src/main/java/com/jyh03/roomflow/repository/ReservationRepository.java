package com.jyh03.roomflow.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

import com.jyh03.roomflow.domain.reservation.Reservation;
import com.jyh03.roomflow.domain.reservation.ReservationStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("select r.room.id from Reservation r where r.id = :id")
    Optional<Long> findRoomId(@Param("id") Long id);

    List<Reservation> findByUserIdOrderByStartAtDesc(Long userId);

    @Query("""
            select r.id from Reservation r where
            (r.status = com.jyh03.roomflow.domain.reservation.ReservationStatus.CONFIRMED
             and r.checkedInAt is null
             and ((r.startAt <= :cutoff and r.createdAt <= :cutoff) or r.endAt <= :now))
            or (r.status = com.jyh03.roomflow.domain.reservation.ReservationStatus.CHECKED_IN and r.endAt <= :now)
            order by r.id
            """)
    List<Long> findDueIds(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now, Pageable page);

    @Query("""
            select r from Reservation r
            where r.status = :status and r.checkedInAt is null
              and ((r.startAt <= :cutoff and r.createdAt <= :cutoff) or r.endAt <= :now)
            """)
    List<Reservation> findNoShowCandidates(
            @Param("status") ReservationStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now);

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

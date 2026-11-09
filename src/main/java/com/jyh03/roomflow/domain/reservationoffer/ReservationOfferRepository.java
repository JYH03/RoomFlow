package com.jyh03.roomflow.domain.reservationoffer;

import com.jyh03.roomflow.domain.reservationoffer.ReservationOffer;
import com.jyh03.roomflow.domain.reservationoffer.ReservationOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface ReservationOfferRepository
                extends JpaRepository<ReservationOffer, Long> {

        @Query("select o.waitingQueue.room.id from ReservationOffer o where o.id = :id")
        Optional<Long> findRoomId(@Param("id") Long id);

        List<ReservationOffer> findByWaitingQueueUserIdOrderByCreatedAtDesc(Long userId);
        List<ReservationOffer> findByWaitingQueueIdAndStatus(Long waitingQueueId, ReservationOfferStatus status);

        @Query("select o.id from ReservationOffer o where o.status = :status and o.expiresAt <= :now order by o.id")
        List<Long> findExpiredIds(@Param("status") ReservationOfferStatus status,
                        @Param("now") LocalDateTime now, Pageable page);

        // PENDING holds the slot until expiration is processed atomically with the next promotion.
        boolean existsByWaitingQueueRoomIdAndWaitingQueueStartAtLessThanAndWaitingQueueEndAtGreaterThanAndStatus(
                        Long roomId, LocalDateTime endAt, LocalDateTime startAt, ReservationOfferStatus status);

        List<ReservationOffer> findByWaitingQueueRoomIdAndWaitingQueueStartAtLessThanAndWaitingQueueEndAtGreaterThanAndStatus(
                        Long roomId, LocalDateTime endAt, LocalDateTime startAt, ReservationOfferStatus status);

        List<ReservationOffer> findByStatusAndExpiresAtLessThanEqual(
                        ReservationOfferStatus status,
                        LocalDateTime now);

        boolean existsByWaitingQueueRoomIdAndWaitingQueueStartAtLessThanAndWaitingQueueEndAtGreaterThanAndStatusAndExpiresAtGreaterThan(
                        Long roomId,
                        LocalDateTime endAt,
                        LocalDateTime startAt,
                        ReservationOfferStatus status,
                        LocalDateTime now);
}

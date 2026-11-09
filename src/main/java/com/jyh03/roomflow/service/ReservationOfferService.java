package com.jyh03.roomflow.service;

import com.jyh03.roomflow.domain.reservation.*;
import com.jyh03.roomflow.domain.reservationoffer.*;
import com.jyh03.roomflow.domain.waitingqueue.*;
import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.dto.reservation.ReservationResponse;
import com.jyh03.roomflow.dto.offer.ReservationOfferResponse;
import com.jyh03.roomflow.repository.*;
import com.jyh03.roomflow.security.Ownership;
import com.jyh03.roomflow.global.error.BookingConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ReservationOfferService {
    private static final List<ReservationStatus> ACTIVE = List.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);
    private static final List<WaitingQueueStatus> ACTIVE_QUEUE = List.of(WaitingQueueStatus.WAITING, WaitingQueueStatus.OFFERED);
    private final ReservationOfferRepository reservationOfferRepository;
    private final ReservationRepository reservationRepository;
    private final WaitingQueueRepository waitingQueueRepository;
    private final BookingLocks locks;

    public ReservationOfferService(ReservationOfferRepository reservationOfferRepository,
            ReservationRepository reservationRepository, WaitingQueueRepository waitingQueueRepository,
            BookingLocks locks) {
        this.reservationOfferRepository = reservationOfferRepository;
        this.reservationRepository = reservationRepository;
        this.waitingQueueRepository = waitingQueueRepository;
        this.locks = locks;
    }

    public ReservationResponse acceptOffer(Long id, Long userId) {
        locks.user(userId);
        locks.offerRoom(id);
        ReservationOffer offer = find(id);
        WaitingQueue queue = offer.getWaitingQueue();
        Ownership.requireOwner(queue.getUser().getId(), userId);
        validatePending(offer);
        if (!queue.getRoom().isActive()) throw new BookingConflictException("사용할 수 없는 미팅룸입니다.");
        if (reservationRepository.existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                queue.getRoom().getId(), queue.getEndAt(), queue.getStartAt(), ACTIVE)) {
            throw new BookingConflictException("해당 시간대에 이미 예약이 있습니다.");
        }
        if (reservationRepository.existsByUserIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                userId, queue.getEndAt(), queue.getStartAt(), ACTIVE)) {
            throw new BookingConflictException("해당 시간대에 이미 예약이 있어 제안을 수락할 수 없습니다.");
        }
        offer.accept();
        queue.accept();
        return ReservationResponse.from(reservationRepository.save(
                new Reservation(queue.getUser(), queue.getRoom(), queue.getStartAt(), queue.getEndAt())));
    }

    public ReservationOfferResponse declineOffer(Long id, Long userId) {
        locks.user(userId);
        locks.offerRoom(id);
        ReservationOffer offer = find(id);
        Ownership.requireOwner(offer.getWaitingQueue().getUser().getId(), userId);
        validatePending(offer);
        offer.decline();
        WaitingQueue queue = offer.getWaitingQueue();
        queue.cancel();
        offerToNextWaitingUser(queue.getRoom(), queue.getStartAt(), queue.getEndAt());
        return ReservationOfferResponse.from(offer);
    }

    public void expireOffer(Long id) {
        locks.offerRoom(id);
        ReservationOffer offer = find(id);
        if (offer.getStatus() != ReservationOfferStatus.PENDING
                || LocalDateTime.now().isBefore(offer.getExpiresAt())) return;
        offer.expire();
        WaitingQueue queue = offer.getWaitingQueue();
        if (queue.getStatus() == WaitingQueueStatus.OFFERED) {
            queue.expire();
        } else if (queue.getStatus() != WaitingQueueStatus.CANCELED) {
            return;
        }
        offerToNextWaitingUser(queue.getRoom(), queue.getStartAt(), queue.getEndAt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void expireQueueOffers(Long queueId) {
        reservationOfferRepository.findByWaitingQueueIdAndStatus(queueId, ReservationOfferStatus.PENDING)
                .forEach(ReservationOffer::expire);
    }

    // The caller holds the room lock in the same transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelQueueOffer(WaitingQueue queue) {
        reservationOfferRepository.findByWaitingQueueIdAndStatus(queue.getId(), ReservationOfferStatus.PENDING)
                .forEach(ReservationOffer::decline);
        offerToNextWaitingUser(queue.getRoom(), queue.getStartAt(), queue.getEndAt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void offerToNextWaitingUser(Room room, LocalDateTime start, LocalDateTime end) {
        locks.room(room.getId());
        LocalDateTime now = LocalDateTime.now();
        if (!room.isActive() || !end.isAfter(now)) return;
        if (reservationRepository.existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                room.getId(), end, start, ACTIVE) || hasPendingOffer(room.getId(), start, end)) return;

        while (true) {
            WaitingQueue next = waitingQueueRepository.findFirstByRoomIdAndStartAtAndStatusOrderByQueueOrderAsc(
                    room.getId(), start, WaitingQueueStatus.WAITING).orElse(null);
            if (next == null) return;
            if (reservationRepository.existsByUserIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                    next.getUser().getId(), end, start, ACTIVE)) {
                next.expire();
                // Flush before selecting the next queue entry.
                waitingQueueRepository.flush();
                continue;
            }
            LocalDateTime deadline = now.plusMinutes(10);
            if (deadline.isAfter(end)) deadline = end;
            next.offer();
            reservationOfferRepository.save(new ReservationOffer(next, deadline));
            return;
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public boolean hasPendingOffer(Long roomId, LocalDateTime start, LocalDateTime end) {
        return reservationOfferRepository
                .existsByWaitingQueueRoomIdAndWaitingQueueStartAtLessThanAndWaitingQueueEndAtGreaterThanAndStatus(
                        roomId, end, start, ReservationOfferStatus.PENDING);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public boolean isHeldForQueue(Long roomId, LocalDateTime start, LocalDateTime end) {
        return hasPendingOffer(roomId, start, end) || waitingQueueRepository
                .existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(roomId, end, start, ACTIVE_QUEUE);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ReservationOfferResponse> getMyOffers(Long userId) {
        return reservationOfferRepository.findByWaitingQueueUserIdOrderByCreatedAtDesc(userId)
                .stream().map(ReservationOfferResponse::from).toList();
    }

    private ReservationOffer find(Long id) {
        return reservationOfferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 제안입니다."));
    }

    private void validatePending(ReservationOffer offer) {
        WaitingQueue queue = offer.getWaitingQueue();
        if (offer.getStatus() != ReservationOfferStatus.PENDING
                || queue.getStatus() != WaitingQueueStatus.OFFERED
                || !LocalDateTime.now().isBefore(offer.getExpiresAt())
                || !LocalDateTime.now().isBefore(queue.getEndAt())) {
            throw new BookingConflictException("수락 또는 거절할 수 없는 제안입니다. 상태와 기한을 확인하세요.");
        }
    }
}

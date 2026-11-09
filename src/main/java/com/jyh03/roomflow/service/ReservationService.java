package com.jyh03.roomflow.service;

import java.util.List;
import java.time.LocalDateTime;
import com.jyh03.roomflow.domain.reservation.*;
import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.domain.user.User;
import com.jyh03.roomflow.dto.reservation.*;
import com.jyh03.roomflow.repository.*;
import com.jyh03.roomflow.global.error.BookingConflictException;
import com.jyh03.roomflow.security.Ownership;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ReservationService {
    private static final List<ReservationStatus> ACTIVE = List.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);
    private final ReservationRepository reservationRepository;
    private final ReservationOfferService reservationOfferService;
    private final BookingLocks locks;

    public ReservationService(UserRepository userRepository, RoomRepository roomRepository,
            ReservationRepository reservationRepository, ReservationOfferService reservationOfferService,
            BookingLocks locks) {
        this.reservationRepository = reservationRepository;
        this.reservationOfferService = reservationOfferService;
        this.locks = locks;
    }

    public ReservationResponse createReservation(Long userId, CreateReservationRequest request) {
        BookingPolicy.validateNewSlot(request.startAt(), request.endAt());
        User user = locks.user(userId);
        Room room = locks.room(request.roomId());
        if (!room.isActive()) throw new IllegalArgumentException("현재 사용할 수 없는 미팅룸입니다.");
        if (reservationRepository.existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                room.getId(), request.endAt(), request.startAt(), ACTIVE)) {
            throw new BookingConflictException("해당 시간대는 이미 예약되어 있습니다.");
        }
        if (reservationOfferService.isHeldForQueue(room.getId(), request.startAt(), request.endAt())) {
            throw new BookingConflictException("대기자에게 우선 배정 중인 시간대입니다.");
        }
        if (reservationRepository.existsByUserIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                userId, request.endAt(), request.startAt(), ACTIVE)) {
            throw new BookingConflictException("같은 시간대에 이미 예약한 미팅룸이 있습니다.");
        }
        return ReservationResponse.from(reservationRepository.save(
                new Reservation(user, room, request.startAt(), request.endAt())));
    }

    public ReservationResponse cancelReservation(Long id, Long userId) {
        locks.user(userId);
        locks.reservationRoom(id);
        Reservation reservation = find(id);
        Ownership.requireOwner(reservation.getUser().getId(), userId);
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BookingConflictException("확정된 예약만 취소할 수 있습니다.");
        }
        reservation.cancel();
        reservationOfferService.offerToNextWaitingUser(
                reservation.getRoom(), reservation.getStartAt(), reservation.getEndAt());
        return ReservationResponse.from(reservation);
    }

    public ReservationResponse checkInReservation(Long id, Long userId) {
        locks.user(userId);
        locks.reservationRoom(id);
        Reservation reservation = find(id);
        Ownership.requireOwner(reservation.getUser().getId(), userId);
        reservation.checkIn(LocalDateTime.now());
        return ReservationResponse.from(reservation);
    }

    // Each scheduler item gets its own transaction, holding only one room lock.
    public void processDueReservation(Long id) {
        locks.reservationRoom(id);
        Reservation reservation = find(id);
        LocalDateTime now = LocalDateTime.now();
        if (reservation.markNoShow(now)) {
            reservationOfferService.offerToNextWaitingUser(
                    reservation.getRoom(), reservation.getStartAt(), reservation.getEndAt());
        } else {
            reservation.complete(now);
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ReservationResponse> getMyReservations(Long userId) {
        return reservationRepository.findByUserIdOrderByStartAtDesc(userId).stream()
                .map(ReservationResponse::from).toList();
    }

    private Reservation find(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));
    }
}

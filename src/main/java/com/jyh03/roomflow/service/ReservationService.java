package com.jyh03.roomflow.service;

import java.util.List;

import com.jyh03.roomflow.domain.reservation.Reservation;
import com.jyh03.roomflow.domain.reservation.ReservationStatus;
import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.domain.user.User;
import com.jyh03.roomflow.dto.reservation.CreateReservationRequest;
import com.jyh03.roomflow.dto.reservation.ReservationResponse;
import com.jyh03.roomflow.repository.ReservationRepository;
import com.jyh03.roomflow.repository.RoomRepository;
import com.jyh03.roomflow.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN);

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;

    public ReservationService(
            UserRepository userRepository,
            RoomRepository roomRepository,
            ReservationRepository reservationRepository) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request) {
        validateReservationTime(request);

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 미팅룸입니다."));

        if (!room.isActive()) {
            throw new IllegalArgumentException("현재 사용할 수 없는 미팅룸입니다.");
        }

        boolean roomAlreadyReserved = reservationRepository
                .existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                        room.getId(),
                        request.endAt(),
                        request.startAt(),
                        ACTIVE_RESERVATION_STATUSES);

        if (roomAlreadyReserved) {
            throw new IllegalArgumentException("해당 시간대는 이미 예약되어 있습니다.");
        }

        boolean userAlreadyReserved = reservationRepository
                .existsByUserIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                        user.getId(),
                        request.endAt(),
                        request.startAt(),
                        ACTIVE_RESERVATION_STATUSES);

        if (userAlreadyReserved) {
            throw new IllegalArgumentException("같은 시간대에 이미 예약한 미팅룸이 있습니다.");
        }

        Reservation reservation = new Reservation(
                user,
                room,
                request.startAt(),
                request.endAt());

        Reservation savedReservation = reservationRepository.save(reservation);

        return ReservationResponse.from(savedReservation);
    }

    @Transactional
    public ReservationResponse cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalArgumentException("확정된 예약만 취소할 수 있습니다.");
        }

        reservation.cancel();

        return ReservationResponse.from(reservation);
    }

    private void validateReservationTime(CreateReservationRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }

        if (!request.endAt().equals(request.startAt().plusHours(1))) {
            throw new IllegalArgumentException("예약 시간은 1시간 단위여야 합니다.");
        }
    }
}
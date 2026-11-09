package com.jyh03.roomflow.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.jyh03.roomflow.domain.reservation.Reservation;
import com.jyh03.roomflow.domain.reservation.ReservationStatus;
import com.jyh03.roomflow.dto.room.AvailabilitySlotResponse;
import com.jyh03.roomflow.dto.room.RoomAvailabilityResponse;
import com.jyh03.roomflow.dto.room.RoomResponse;
import com.jyh03.roomflow.repository.ReservationRepository;
import com.jyh03.roomflow.repository.RoomRepository;
import com.jyh03.roomflow.domain.reservationoffer.*;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationOfferRepository offerRepository;

    public RoomService(
            RoomRepository roomRepository,
            ReservationRepository reservationRepository, ReservationOfferRepository offerRepository) {
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.offerRepository = offerRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms() {
        return roomRepository.findAll(
                Sort.by(Sort.Direction.ASC, "roomNumber"))
                .stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomAvailabilityResponse getAvailability(Long roomId, LocalDate date) {
        var room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 미팅룸입니다."));

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime nextDayStart = date.plusDays(1).atStartOfDay();
        var offers = offerRepository.findByWaitingQueueRoomIdAndWaitingQueueStartAtLessThanAndWaitingQueueEndAtGreaterThanAndStatus(
                roomId, nextDayStart, startOfDay, ReservationOfferStatus.PENDING);

        List<Reservation> reservations = reservationRepository
                .findByRoomIdAndStartAtGreaterThanEqualAndStartAtLessThanAndStatusIn(
                        roomId,
                        startOfDay,
                        nextDayStart,
                        List.of(
                                ReservationStatus.CONFIRMED,
                                ReservationStatus.CHECKED_IN));

        List<AvailabilitySlotResponse> slots = new ArrayList<>();

        for (int hour = 9; hour < 22; hour++) {
            LocalDateTime startAt = date.atTime(hour, 0);
            LocalDateTime endAt = startAt.plusHours(1);

            boolean available = room.isActive() && startAt.isAfter(LocalDateTime.now()) && reservations.stream()
                    .noneMatch(reservation -> startAt.isBefore(reservation.getEndAt())
                            && endAt.isAfter(reservation.getStartAt()))
                    && offers.stream().noneMatch(offer -> startAt.isBefore(offer.getWaitingQueue().getEndAt())
                            && endAt.isAfter(offer.getWaitingQueue().getStartAt()));

            slots.add(new AvailabilitySlotResponse(startAt, endAt, available));
        }

        return new RoomAvailabilityResponse(roomId, date, slots);
    }
}

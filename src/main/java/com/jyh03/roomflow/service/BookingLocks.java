package com.jyh03.roomflow.service;

import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.domain.user.User;
import com.jyh03.roomflow.domain.reservationoffer.ReservationOfferRepository;
import com.jyh03.roomflow.repository.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lock order: user (if needed), then ONE room. Load mutable entities only after locking. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class BookingLocks {
    private final UserRepository users;
    private final RoomRepository rooms;
    private final ReservationRepository reservations;
    private final WaitingQueueRepository queues;
    private final ReservationOfferRepository offers;

    public BookingLocks(UserRepository users, RoomRepository rooms, ReservationRepository reservations,
            WaitingQueueRepository queues, ReservationOfferRepository offers) {
        this.users = users;
        this.rooms = rooms;
        this.reservations = reservations;
        this.queues = queues;
        this.offers = offers;
    }

    public User user(Long id) {
        return users.lockById(id).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    public Room room(Long id) {
        return rooms.lockById(id).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 미팅룸입니다."));
    }

    public void reservationRoom(Long id) {
        room(reservations.findRoomId(id).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다.")));
    }

    public void waitingRoom(Long id) {
        room(queues.findRoomId(id).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대기열입니다.")));
    }

    public void offerRoom(Long id) {
        room(offers.findRoomId(id).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 제안입니다.")));
    }
}

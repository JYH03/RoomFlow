package com.jyh03.roomflow.service;

import com.jyh03.roomflow.domain.reservation.ReservationStatus;
import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.domain.user.User;
import com.jyh03.roomflow.domain.waitingqueue.*;
import com.jyh03.roomflow.dto.waitingqueue.*;
import com.jyh03.roomflow.repository.*;
import com.jyh03.roomflow.security.Ownership;
import com.jyh03.roomflow.global.error.BookingConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.List;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class WaitingQueueService {
    private static final List<ReservationStatus> ACTIVE = List.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);
    private static final List<WaitingQueueStatus> ACTIVE_QUEUE = List.of(WaitingQueueStatus.WAITING, WaitingQueueStatus.OFFERED);
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final WaitingQueueRepository waitingQueueRepository;
    private final ReservationOfferService offerService;
    private final BookingLocks locks;

    public WaitingQueueService(UserRepository userRepository, RoomRepository roomRepository,
            ReservationRepository reservationRepository, WaitingQueueRepository waitingQueueRepository,
            ReservationOfferService offerService, BookingLocks locks) {
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.waitingQueueRepository = waitingQueueRepository;
        this.offerService = offerService;
        this.locks = locks;
    }

    public WaitingQueueResponse joinWaitingQueue(Long userId, CreateWaitingQueueRequest request) {
        BookingPolicy.validateNewSlot(request.startAt(), request.endAt());
        User user = locks.user(userId);
        Room room = locks.room(request.roomId());
        if (!room.isActive()) throw new IllegalArgumentException("사용할 수 없는 미팅룸입니다.");
        boolean occupied = reservationRepository.existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                room.getId(), request.endAt(), request.startAt(), ACTIVE);
        if (!occupied && !offerService.isHeldForQueue(room.getId(), request.startAt(), request.endAt())) {
            throw new BookingConflictException("예약 가능한 시간대입니다. 대기열 대신 예약해주세요.");
        }
        if (reservationRepository.existsByUserIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                userId, request.endAt(), request.startAt(), ACTIVE)) {
            throw new BookingConflictException("해당 시간대에 이미 예약이 있습니다.");
        }
        if (waitingQueueRepository.existsByUserIdAndRoomIdAndStartAtAndStatusIn(
                userId, room.getId(), request.startAt(), ACTIVE_QUEUE)) {
            throw new BookingConflictException("이미 해당 시간대에 대기 중입니다.");
        }
        int order = waitingQueueRepository.findTopByRoomIdAndStartAtOrderByQueueOrderDesc(room.getId(), request.startAt())
                .map(queue -> queue.getQueueOrder() + 1).orElse(1);
        return toResponse(waitingQueueRepository.save(new WaitingQueue(
                user, room, request.startAt(), request.endAt(), order)));
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<WaitingQueueResponse> getMyWaitingQueues(Long userId) {
        if (!userRepository.existsById(userId)) throw new IllegalArgumentException("존재하지 않는 사용자입니다.");
        return waitingQueueRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(userId, ACTIVE_QUEUE)
                .stream().map(this::toResponse).toList();
    }

    public WaitingQueueResponse cancelWaitingQueue(Long id, Long userId) {
        locks.user(userId);
        locks.waitingRoom(id);
        WaitingQueue queue = waitingQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대기열입니다."));
        Ownership.requireOwner(queue.getUser().getId(), userId);
        if (!ACTIVE_QUEUE.contains(queue.getStatus())) throw new BookingConflictException("취소할 수 없는 대기열입니다.");
        queue.cancel();
        offerService.cancelQueueOffer(queue);
        return toResponse(queue);
    }

    public void expireEndedQueue(Long id) {
        locks.waitingRoom(id);
        WaitingQueue queue = waitingQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대기열입니다."));
        if (ACTIVE_QUEUE.contains(queue.getStatus()) && !queue.getEndAt().isAfter(java.time.LocalDateTime.now())) {
            queue.expire();
            offerService.expireQueueOffers(queue.getId());
        }
    }

    private WaitingQueueResponse toResponse(WaitingQueue queue) {
        Long position = ACTIVE_QUEUE.contains(queue.getStatus())
                ? waitingQueueRepository.countByRoomIdAndStartAtAndQueueOrderLessThanAndStatusIn(
                        queue.getRoom().getId(), queue.getStartAt(), queue.getQueueOrder(), ACTIVE_QUEUE) + 1
                : null;
        return WaitingQueueResponse.from(queue, position);
    }
}

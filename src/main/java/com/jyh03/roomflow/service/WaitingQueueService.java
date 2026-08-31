package com.jyh03.roomflow.service;

import com.jyh03.roomflow.domain.reservation.ReservationStatus;
import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.domain.user.User;
import com.jyh03.roomflow.domain.waitingqueue.WaitingQueue;
import com.jyh03.roomflow.domain.waitingqueue.WaitingQueueStatus;
import com.jyh03.roomflow.dto.waitingqueue.CreateWaitingQueueRequest;
import com.jyh03.roomflow.dto.waitingqueue.WaitingQueueResponse;
import com.jyh03.roomflow.repository.ReservationRepository;
import com.jyh03.roomflow.repository.RoomRepository;
import com.jyh03.roomflow.repository.UserRepository;
import com.jyh03.roomflow.repository.WaitingQueueRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class WaitingQueueService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN);

    private static final List<WaitingQueueStatus> ACTIVE_WAITING_QUEUE_STATUSES = List.of(WaitingQueueStatus.WAITING,
            WaitingQueueStatus.OFFERED);

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final WaitingQueueRepository waitingQueueRepository;

    public WaitingQueueService(
            UserRepository userRepository,
            RoomRepository roomRepository,
            ReservationRepository reservationRepository,
            WaitingQueueRepository waitingQueueRepository) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.waitingQueueRepository = waitingQueueRepository;
    }

    @Transactional
    public WaitingQueueResponse joinWaitingQueue(CreateWaitingQueueRequest request) {
        validateReservationTime(request);

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 미팅룸입니다."));

        if (!room.isActive()) {
            throw new IllegalArgumentException("현재 사용할 수 없는 미팅룸입니다.");
        }

        boolean alreadyReserved = reservationRepository
                .existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                        room.getId(),
                        request.endAt(),
                        request.startAt(),
                        ACTIVE_RESERVATION_STATUSES);

        if (!alreadyReserved) {
            throw new IllegalArgumentException(
                    "예약 가능한 시간대입니다. 대기열 대신 예약을 진행해주세요.");
        }
        boolean userAlreadyReserved = reservationRepository
                .existsByUserIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                        user.getId(),
                        request.endAt(),
                        request.startAt(),
                        ACTIVE_RESERVATION_STATUSES);

        if (userAlreadyReserved) {
            throw new IllegalArgumentException(
                    "이미 해당 시간대에 예약이 있어 대기열에 등록할 수 없습니다.");
        }

        boolean alreadyWaiting = waitingQueueRepository
                .existsByUserIdAndRoomIdAndStartAtAndStatusIn(
                        user.getId(),
                        room.getId(),
                        request.startAt(),
                        ACTIVE_WAITING_QUEUE_STATUSES);

        if (alreadyWaiting) {
            throw new IllegalArgumentException("이미 해당 시간대의 대기열에 등록되어 있습니다.");
        }

        int nextQueueOrder = waitingQueueRepository
                .findTopByRoomIdAndStartAtOrderByQueueOrderDesc(
                        room.getId(),
                        request.startAt())
                .map(waitingQueue -> waitingQueue.getQueueOrder() + 1)
                .orElse(1);

        WaitingQueue waitingQueue = new WaitingQueue(
                user,
                room,
                request.startAt(),
                request.endAt(),
                nextQueueOrder);

        return WaitingQueueResponse.from(waitingQueueRepository.save(waitingQueue));
    }

    @Transactional
    public List<WaitingQueueResponse> getMyWaitingQueues(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("존재하지 않는 사용자입니다.");
        }

        return waitingQueueRepository
                .findByUserIdAndStatusInOrderByCreatedAtDesc(
                        userId,
                        ACTIVE_WAITING_QUEUE_STATUSES)
                .stream()
                .map(WaitingQueueResponse::from)
                .toList();
    }

    @Transactional
    public WaitingQueueResponse cancelWaitingQueue(Long waitingQueueId) {
        WaitingQueue waitingQueue = waitingQueueRepository.findById(waitingQueueId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대기열입니다."));

        if (waitingQueue.getStatus() != WaitingQueueStatus.WAITING
                && waitingQueue.getStatus() != WaitingQueueStatus.OFFERED) {
            throw new IllegalArgumentException("취소할 수 없는 대기열 상태입니다.");
        }

        waitingQueue.cancel();

        return WaitingQueueResponse.from(waitingQueue);
    }

    private void validateReservationTime(CreateWaitingQueueRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }

        if (Duration.between(request.startAt(), request.endAt()).toHours() != 1) {
            throw new IllegalArgumentException("예약과 대기열 시간은 1시간 단위여야 합니다.");
        }
    }
}
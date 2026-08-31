package com.jyh03.roomflow.repository;

import com.jyh03.roomflow.domain.waitingqueue.WaitingQueue;
import com.jyh03.roomflow.domain.waitingqueue.WaitingQueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaitingQueueRepository extends JpaRepository<WaitingQueue, Long> {

        Optional<WaitingQueue> findTopByRoomIdAndStartAtOrderByQueueOrderDesc(
                        Long roomId,
                        LocalDateTime startAt);

        boolean existsByUserIdAndRoomIdAndStartAtAndStatusIn(
                        Long userId,
                        Long roomId,
                        LocalDateTime startAt,
                        Collection<WaitingQueueStatus> statuses);

        List<WaitingQueue> findByUserIdAndStatusInOrderByCreatedAtDesc(
                        Long userId,
                        Collection<WaitingQueueStatus> statuses);
}
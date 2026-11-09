package com.jyh03.roomflow.repository;

import com.jyh03.roomflow.domain.waitingqueue.WaitingQueue;
import com.jyh03.roomflow.domain.waitingqueue.WaitingQueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitingQueueRepository extends JpaRepository<WaitingQueue, Long> {

        @Query("select w.id from WaitingQueue w where w.endAt <= :now and w.status in :statuses order by w.id")
        List<Long> findEndedIds(@Param("now") LocalDateTime now,
                        @Param("statuses") Collection<WaitingQueueStatus> statuses,
                        org.springframework.data.domain.Pageable pageable);

        @Query("select w.room.id from WaitingQueue w where w.id = :id")
        Optional<Long> findRoomId(@Param("id") Long id);

        boolean existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                        Long roomId, LocalDateTime endAt, LocalDateTime startAt,
                        Collection<WaitingQueueStatus> statuses);

        long countByRoomIdAndStartAtAndQueueOrderLessThanAndStatusIn(
                        Long roomId, LocalDateTime startAt, int queueOrder,
                        Collection<WaitingQueueStatus> statuses);

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

        Optional<WaitingQueue> findFirstByRoomIdAndStartAtAndStatusOrderByQueueOrderAsc(
                        Long roomId,
                        LocalDateTime startAt,
                        WaitingQueueStatus status);
}

package com.jyh03.roomflow.dto.waitingqueue;

import com.jyh03.roomflow.domain.waitingqueue.WaitingQueue;
import com.jyh03.roomflow.domain.waitingqueue.WaitingQueueStatus;

import java.time.LocalDateTime;

public record WaitingQueueResponse(
        Long id,
        Long userId,
        Long roomId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer queueOrder,
        WaitingQueueStatus status) {

    public static WaitingQueueResponse from(WaitingQueue waitingQueue) {
        return new WaitingQueueResponse(
                waitingQueue.getId(),
                waitingQueue.getUser().getId(),
                waitingQueue.getRoom().getId(),
                waitingQueue.getStartAt(),
                waitingQueue.getEndAt(),
                waitingQueue.getQueueOrder(),
                waitingQueue.getStatus());
    }
}
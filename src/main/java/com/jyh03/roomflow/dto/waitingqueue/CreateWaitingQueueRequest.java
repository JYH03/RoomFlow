package com.jyh03.roomflow.dto.waitingqueue;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateWaitingQueueRequest(

        @NotNull(message = "사용자 ID는 필수입니다.") Long userId,

        @NotNull(message = "미팅룸 ID는 필수입니다.") Long roomId,

        @NotNull(message = "예약 시작 시간은 필수입니다.") LocalDateTime startAt,

        @NotNull(message = "예약 종료 시간은 필수입니다.") LocalDateTime endAt) {
}
package com.jyh03.roomflow.dto.reservation;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public record CreateReservationRequest(

        @NotNull(message = "미팅룸 ID는 필수입니다.") Long roomId,

        @NotNull(message = "예약 시작 시간은 필수입니다.") LocalDateTime startAt,

        @NotNull(message = "예약 종료 시간은 필수입니다.") LocalDateTime endAt) {
}

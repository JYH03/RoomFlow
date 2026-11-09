package com.jyh03.roomflow.service;

import java.time.LocalDateTime;

public final class BookingPolicy {
    private BookingPolicy() { }

    public static void validateNewSlot(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.equals(start.plusHours(1))) {
            throw new IllegalArgumentException("예약과 대기열은 정확히 1시간이어야 합니다.");
        }
        if (start.getMinute() != 0 || start.getSecond() != 0 || start.getNano() != 0
                || start.getHour() < 9 || start.getHour() >= 22 || !end.toLocalDate().equals(start.toLocalDate())) {
            throw new IllegalArgumentException("09:00~22:00 사이의 정각 1시간 슬롯을 선택하세요.");
        }
        if (!start.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("이미 시작된 시간대에는 새 예약 또는 대기를 등록할 수 없습니다.");
        }
    }
}

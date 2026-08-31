package com.jyh03.roomflow.dto.room;

import java.time.LocalDateTime;

public record AvailabilitySlotResponse(
                LocalDateTime startAt,
                LocalDateTime endAt,
                boolean available) {

}
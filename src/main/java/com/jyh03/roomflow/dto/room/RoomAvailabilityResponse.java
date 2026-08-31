package com.jyh03.roomflow.dto.room;

import java.time.LocalDate;
import java.util.List;

public record RoomAvailabilityResponse(
        Long roomId,
        LocalDate date,
        List<AvailabilitySlotResponse> slots) {

}

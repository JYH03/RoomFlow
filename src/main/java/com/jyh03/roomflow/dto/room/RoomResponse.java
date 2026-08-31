package com.jyh03.roomflow.dto.room;

import com.jyh03.roomflow.domain.room.Room;

public record RoomResponse(Long id,
        int roomnumber,
        String name,
        boolean active) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getRoomNumber(),
                room.getName(),
                room.isActive());
    }
}

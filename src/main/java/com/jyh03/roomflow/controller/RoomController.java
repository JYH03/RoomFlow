package com.jyh03.roomflow.controller;

import java.time.LocalDate;
import java.util.List;

import com.jyh03.roomflow.dto.room.RoomAvailabilityResponse;
import com.jyh03.roomflow.dto.room.RoomResponse;
import com.jyh03.roomflow.service.RoomService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirements
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomResponse> getRooms() {
        return roomService.getRooms();
    }

    @GetMapping("/{roomId}/availability")
    public RoomAvailabilityResponse getAvailability(
            @PathVariable("roomId") Long roomId,
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return roomService.getAvailability(roomId, date);
    }
}

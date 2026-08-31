package com.jyh03.roomflow.controller;

import com.jyh03.roomflow.dto.reservation.CreateReservationRequest;
import com.jyh03.roomflow.dto.reservation.ReservationResponse;
import com.jyh03.roomflow.service.ReservationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse createReservation(
            @Valid @RequestBody CreateReservationRequest request) {
        return reservationService.createReservation(request);
    }

    @PostMapping("/{reservationId}/cancel")
    public ReservationResponse cancelReservation(
            @PathVariable("reservationId") Long reservationId) {
        return reservationService.cancelReservation(reservationId);
    }
}
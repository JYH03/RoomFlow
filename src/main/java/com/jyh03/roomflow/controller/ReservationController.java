package com.jyh03.roomflow.controller;

import com.jyh03.roomflow.dto.reservation.CreateReservationRequest;
import com.jyh03.roomflow.dto.reservation.ReservationResponse;
import com.jyh03.roomflow.service.ReservationService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return reservationService.createReservation(Long.valueOf(jwt.getSubject()), request);
    }

    @GetMapping
    public List<ReservationResponse> getMyReservations(@AuthenticationPrincipal Jwt jwt) {
        return reservationService.getMyReservations(Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/{reservationId}/check-in")
    public ReservationResponse checkInReservation(
            @PathVariable("reservationId") Long reservationId,
            @AuthenticationPrincipal Jwt jwt) {
        return reservationService.checkInReservation(reservationId, Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/{reservationId}/cancel")
    public ReservationResponse cancelReservation(
            @PathVariable("reservationId") Long reservationId,
            @AuthenticationPrincipal Jwt jwt) {
        return reservationService.cancelReservation(reservationId, Long.valueOf(jwt.getSubject()));
    }
}

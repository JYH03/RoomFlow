package com.jyh03.roomflow.controller;

import com.jyh03.roomflow.dto.reservation.ReservationResponse;
import com.jyh03.roomflow.service.ReservationOfferService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import com.jyh03.roomflow.dto.offer.ReservationOfferResponse;
import java.util.List;

@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/reservation-offers")
public class ReservationOfferController {

    private final ReservationOfferService reservationOfferService;

    public ReservationOfferController(ReservationOfferService reservationOfferService) {
        this.reservationOfferService = reservationOfferService;
    }

    @GetMapping
    public List<ReservationOfferResponse> getMyOffers(@AuthenticationPrincipal Jwt jwt) {
        return reservationOfferService.getMyOffers(Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/{reservationOfferId}/decline")
    public ReservationOfferResponse declineOffer(@PathVariable("reservationOfferId") Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return reservationOfferService.declineOffer(id, Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/{reservationOfferId}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse acceptOffer(
            @PathVariable("reservationOfferId") Long reservationOfferId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return reservationOfferService.acceptOffer(reservationOfferId, Long.valueOf(jwt.getSubject()));
    }
}

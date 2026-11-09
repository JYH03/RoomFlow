package com.jyh03.roomflow.dto.offer;

import com.jyh03.roomflow.domain.reservationoffer.*;
import java.time.LocalDateTime;

public record ReservationOfferResponse(Long id, Long waitingQueueId, Long roomId,
        LocalDateTime startAt, LocalDateTime endAt, LocalDateTime expiresAt,
        ReservationOfferStatus status) {
    public static ReservationOfferResponse from(ReservationOffer offer) {
        var queue = offer.getWaitingQueue();
        return new ReservationOfferResponse(offer.getId(), queue.getId(), queue.getRoom().getId(),
                queue.getStartAt(), queue.getEndAt(), offer.getExpiresAt(), offer.getStatus());
    }
}

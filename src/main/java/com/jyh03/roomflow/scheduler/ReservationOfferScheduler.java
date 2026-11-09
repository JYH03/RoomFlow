package com.jyh03.roomflow.scheduler;

import com.jyh03.roomflow.service.ReservationOfferService;
import com.jyh03.roomflow.domain.reservationoffer.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "roomflow.offer-expiration.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationOfferScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationOfferScheduler.class);
    private final ReservationOfferService service;
    private final ReservationOfferRepository offers;
    public ReservationOfferScheduler(ReservationOfferService service, ReservationOfferRepository offers) {
        this.service = service;
        this.offers = offers;
    }
    @Scheduled(fixedDelayString = "${roomflow.offer-expiration.delay-ms:30000}",
            initialDelayString = "${roomflow.offer-expiration.delay-ms:30000}")
    public void expireOffers() {
        for (Long id : offers.findExpiredIds(ReservationOfferStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, 100))) {
            try {
                service.expireOffer(id);
            } catch (RuntimeException error) {
                log.warn("Offer {} expiration failed; will retry next cycle", id, error);
            }
        }
    }
}

package com.jyh03.roomflow.scheduler;

import com.jyh03.roomflow.service.ReservationService;
import com.jyh03.roomflow.repository.ReservationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "roomflow.no-show.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationNoShowScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationNoShowScheduler.class);
    private final ReservationService service;
    private final ReservationRepository reservations;
    private final com.jyh03.roomflow.repository.WaitingQueueRepository queues;
    private final com.jyh03.roomflow.service.WaitingQueueService queueService;
    public ReservationNoShowScheduler(ReservationService service, ReservationRepository reservations,
            com.jyh03.roomflow.repository.WaitingQueueRepository queues,
            com.jyh03.roomflow.service.WaitingQueueService queueService) {
        this.service = service;
        this.reservations = reservations;
        this.queues = queues;
        this.queueService = queueService;
    }
    @Scheduled(fixedDelayString = "${roomflow.no-show.delay-ms:30000}",
            initialDelayString = "${roomflow.no-show.delay-ms:30000}")
    public void processNoShows() {
        LocalDateTime now = LocalDateTime.now();
        for (Long id : reservations.findDueIds(now.minusMinutes(5), now, PageRequest.of(0, 100))) {
            try {
                service.processDueReservation(id);
            } catch (RuntimeException error) {
                log.warn("Reservation {} maintenance failed; will retry next cycle", id, error);
            }
        }
        var active = java.util.List.of(com.jyh03.roomflow.domain.waitingqueue.WaitingQueueStatus.WAITING,
                com.jyh03.roomflow.domain.waitingqueue.WaitingQueueStatus.OFFERED);
        for (Long id : queues.findEndedIds(now, active, PageRequest.of(0, 100))) {
            try {
                queueService.expireEndedQueue(id);
            } catch (RuntimeException error) {
                log.warn("Queue {} maintenance failed; will retry next cycle", id, error);
            }
        }
    }
}

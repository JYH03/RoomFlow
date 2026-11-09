package com.jyh03.roomflow.service;

import com.jyh03.roomflow.domain.reservationoffer.ReservationOffer;
import com.jyh03.roomflow.domain.reservation.Reservation;
import com.jyh03.roomflow.domain.reservation.ReservationStatus;
import org.springframework.test.util.ReflectionTestUtils;
import com.jyh03.roomflow.domain.reservationoffer.ReservationOfferRepository;
import com.jyh03.roomflow.domain.reservationoffer.ReservationOfferStatus;
import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.domain.user.User;
import com.jyh03.roomflow.domain.waitingqueue.WaitingQueue;
import com.jyh03.roomflow.domain.waitingqueue.WaitingQueueStatus;
import com.jyh03.roomflow.repository.ReservationRepository;
import com.jyh03.roomflow.repository.WaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationOfferServiceTests {

    private ReservationOfferRepository offers;
    private ReservationRepository reservations;
    private WaitingQueueRepository queues;
    private ReservationOfferService service;
    private Room room;
    private LocalDateTime startAt;

    @BeforeEach
    void setUp() {
        offers = mock(ReservationOfferRepository.class);
        reservations = mock(ReservationRepository.class);
        queues = mock(WaitingQueueRepository.class);
        service = new ReservationOfferService(offers, reservations, queues, mock(BookingLocks.class));
        room = mock(Room.class);
        when(room.getId()).thenReturn(1L);
        when(room.isActive()).thenReturn(true);
        startAt = LocalDateTime.now().plusDays(1);
    }

    private WaitingQueue waitingQueue(int order) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        return new WaitingQueue(user, room, startAt, startAt.plusHours(1), order);
    }

    private ReservationOffer expiredOffer(WaitingQueue queue) {
        queue.offer();
        ReservationOffer offer = new ReservationOffer(queue, LocalDateTime.now().minusMinutes(1));
        when(offers.findById(1L)).thenReturn(Optional.of(offer));
        return offer;
    }

    @Test
    void expiresOldOfferAndPromotesNextWaitingUserOnlyOnce() {
        WaitingQueue first = waitingQueue(1);
        ReservationOffer oldOffer = expiredOffer(first);
        WaitingQueue next = waitingQueue(2);
        when(queues.findFirstByRoomIdAndStartAtAndStatusOrderByQueueOrderAsc(
                1L, startAt, WaitingQueueStatus.WAITING)).thenReturn(Optional.of(next));
        LocalDateTime before = LocalDateTime.now();

        service.expireOffer(1L);
        service.expireOffer(1L);

        assertEquals(ReservationOfferStatus.EXPIRED, oldOffer.getStatus());
        assertNull(oldOffer.getRespondedAt());
        assertEquals(WaitingQueueStatus.EXPIRED, first.getStatus());
        assertEquals(WaitingQueueStatus.OFFERED, next.getStatus());
        ArgumentCaptor<ReservationOffer> saved = ArgumentCaptor.forClass(ReservationOffer.class);
        verify(offers, times(1)).save(saved.capture());
        assertSame(next, saved.getValue().getWaitingQueue());
        assertEquals(ReservationOfferStatus.PENDING, saved.getValue().getStatus());
        assertFalse(saved.getValue().getExpiresAt().isBefore(before.plusMinutes(10)));
        assertFalse(saved.getValue().getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(10)));
    }

    @Test
    void expiresWithoutCreatingOfferWhenQueueIsEmpty() {
        ReservationOffer offer = expiredOffer(waitingQueue(1));
        service.expireOffer(1L);
        assertEquals(ReservationOfferStatus.EXPIRED, offer.getStatus());
        verify(offers, never()).save(any());
    }

    @Test
    void keepsCanceledQueueCanceled() {
        WaitingQueue queue = waitingQueue(1);
        ReservationOffer offer = expiredOffer(queue);
        queue.cancel();
        service.expireOffer(1L);
        assertEquals(ReservationOfferStatus.EXPIRED, offer.getStatus());
        assertEquals(WaitingQueueStatus.CANCELED, queue.getStatus());
    }

    @Test
    void doesNotPromotePastTimeSlot() {
        startAt = LocalDateTime.now().minusHours(2);
        ReservationOffer offer = expiredOffer(waitingQueue(1));
        service.expireOffer(1L);
        assertEquals(ReservationOfferStatus.EXPIRED, offer.getStatus());
        verifyNoInteractions(queues, reservations);
    }

    @Test
    void doesNotPromoteReservedRoom() {
        expiredOffer(waitingQueue(1));
        when(reservations.existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                eq(1L), any(), any(), anyCollection())).thenReturn(true);
        service.expireOffer(1L);
        verifyNoInteractions(queues);
        verify(offers, never()).save(any());
    }

    @Test
    void doesNotCreateSecondLiveOffer() {
        expiredOffer(waitingQueue(1));
        when(offers.existsByWaitingQueueRoomIdAndWaitingQueueStartAtLessThanAndWaitingQueueEndAtGreaterThanAndStatus(
                eq(1L), any(), any(), eq(ReservationOfferStatus.PENDING))).thenReturn(true);
        service.expireOffer(1L);
        verifyNoInteractions(queues);
        verify(offers, never()).save(any());
    }

    @Test
    void keepsTenMinuteWindowAcrossStart() {
        startAt = LocalDateTime.now().plusMinutes(5);
        expiredOffer(waitingQueue(1));
        WaitingQueue next = waitingQueue(2);
        when(queues.findFirstByRoomIdAndStartAtAndStatusOrderByQueueOrderAsc(
                1L, startAt, WaitingQueueStatus.WAITING)).thenReturn(Optional.of(next));
        service.expireOffer(1L);
        ArgumentCaptor<ReservationOffer> saved = ArgumentCaptor.forClass(ReservationOffer.class);
        verify(offers).save(saved.capture());
        assertTrue(saved.getValue().getExpiresAt().isAfter(startAt));
        assertTrue(saved.getValue().getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(11)));
    }

    @Test
    void rejectsExpiredOfferBeforeSchedulerRuns() {
        ReservationOffer offer = expiredOffer(waitingQueue(1));
        when(offers.findById(1L)).thenReturn(Optional.of(offer));
        assertThrows(IllegalArgumentException.class, () -> service.acceptOffer(1L, 1L));
        verifyNoInteractions(reservations);
        assertEquals(ReservationOfferStatus.PENDING, offer.getStatus());
    }

    @Test
    void leavesAcceptedOfferUntouched() {
        WaitingQueue queue = waitingQueue(1);
        ReservationOffer offer = expiredOffer(queue);
        offer.accept();
        queue.accept();
        service.expireOffer(1L);
        assertEquals(ReservationOfferStatus.ACCEPTED, offer.getStatus());
        assertEquals(WaitingQueueStatus.ACCEPTED, queue.getStatus());
        verify(offers, never()).save(any());
    }

    @Test
    void promotesDuringSlotForNoShowRecovery() {
        startAt = LocalDateTime.now().minusMinutes(6);
        WaitingQueue next = waitingQueue(2);
        when(queues.findFirstByRoomIdAndStartAtAndStatusOrderByQueueOrderAsc(
                1L, startAt, WaitingQueueStatus.WAITING)).thenReturn(Optional.of(next));
        LocalDateTime before = LocalDateTime.now();
        service.offerToNextWaitingUser(room, startAt, startAt.plusHours(1));
        ArgumentCaptor<ReservationOffer> saved = ArgumentCaptor.forClass(ReservationOffer.class);
        verify(offers).save(saved.capture());
        assertEquals(WaitingQueueStatus.OFFERED, next.getStatus());
        assertFalse(saved.getValue().getExpiresAt().isBefore(before.plusMinutes(10)));
    }

    @Test
    void capsInProgressOfferAtSlotEnd() {
        startAt = LocalDateTime.now().minusMinutes(56);
        WaitingQueue next = waitingQueue(2);
        when(queues.findFirstByRoomIdAndStartAtAndStatusOrderByQueueOrderAsc(
                1L, startAt, WaitingQueueStatus.WAITING)).thenReturn(Optional.of(next));
        service.offerToNextWaitingUser(room, startAt, startAt.plusHours(1));
        ArgumentCaptor<ReservationOffer> saved = ArgumentCaptor.forClass(ReservationOffer.class);
        verify(offers).save(saved.capture());
        assertEquals(startAt.plusHours(1), saved.getValue().getExpiresAt());
    }

    @Test
    void acceptingNoShowOfferPreservesSlotAndAllowsCheckIn() {
        startAt = LocalDateTime.now().minusMinutes(15);
        WaitingQueue queue = waitingQueue(2);
        queue.offer();
        ReservationOffer offer = new ReservationOffer(queue, LocalDateTime.now().plusMinutes(10));
        when(offers.findById(1L)).thenReturn(Optional.of(offer));
        when(reservations.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            ReflectionTestUtils.setField(reservation, "createdAt", LocalDateTime.now());
            return reservation;
        });
        assertEquals(ReservationStatus.CONFIRMED, service.acceptOffer(1L, 1L).status());
        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservations).save(saved.capture());
        Reservation reservation = saved.getValue();
        assertEquals(startAt, reservation.getStartAt());
        assertEquals(startAt.plusHours(1), reservation.getEndAt());
        assertFalse(reservation.markNoShow(LocalDateTime.now()));
        reservation.checkIn(LocalDateTime.now());
        assertEquals(ReservationStatus.CHECKED_IN, reservation.getStatus());
    }

    @Test
    void rejectsOfferForAlreadyEndedSlotEvenIfOfferHasTimeLeft() {
        startAt = LocalDateTime.now().minusHours(2);
        WaitingQueue queue = waitingQueue(2);
        queue.offer();
        ReservationOffer offer = new ReservationOffer(queue, LocalDateTime.now().plusMinutes(10));
        when(offers.findById(1L)).thenReturn(Optional.of(offer));
        assertThrows(IllegalArgumentException.class, () -> service.acceptOffer(1L, 1L));
        assertEquals(ReservationOfferStatus.PENDING, offer.getStatus());
        verifyNoInteractions(reservations);
    }
}

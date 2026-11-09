package com.jyh03.roomflow.service;

import com.jyh03.roomflow.domain.reservation.Reservation;
import com.jyh03.roomflow.domain.reservation.ReservationStatus;
import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.domain.user.User;
import com.jyh03.roomflow.repository.ReservationRepository;
import com.jyh03.roomflow.repository.RoomRepository;
import com.jyh03.roomflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationCheckInTests {

    private final LocalDateTime start = LocalDateTime.of(2030, 1, 1, 14, 0);

    private Reservation reservation(LocalDateTime startAt, LocalDateTime createdAt) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        Reservation reservation = new Reservation(user, mock(Room.class),
                startAt, startAt.plusHours(1));
        ReflectionTestUtils.setField(reservation, "createdAt", createdAt);
        return reservation;
    }

    @Test
    void allowsCheckInAtStartAndRecordsTimestamp() {
        Reservation reservation = reservation(start, start.minusDays(1));
        reservation.checkIn(start);
        assertEquals(ReservationStatus.CHECKED_IN, reservation.getStatus());
        assertEquals(start, reservation.getCheckedInAt());
        assertFalse(reservation.markNoShow(start.plusMinutes(10)));
    }

    @Test
    void allowsCheckInJustBeforeDeadline() {
        Reservation reservation = reservation(start, start.minusDays(1));
        reservation.checkIn(start.plusMinutes(5).minusNanos(1));
        assertEquals(ReservationStatus.CHECKED_IN, reservation.getStatus());
    }

    @Test
    void rejectsEarlyCheckInWithoutChangingState() {
        Reservation reservation = reservation(start, start.minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> reservation.checkIn(start.minusSeconds(1)));
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertNull(reservation.getCheckedInAt());
    }

    @Test
    void deadlineBelongsToNoShowNotCheckIn() {
        Reservation reservation = reservation(start, start.minusDays(1));
        LocalDateTime deadline = start.plusMinutes(5);
        assertThrows(IllegalArgumentException.class, () -> reservation.checkIn(deadline));
        assertTrue(reservation.markNoShow(deadline));
        assertEquals(ReservationStatus.NO_SHOW, reservation.getStatus());
        assertFalse(reservation.markNoShow(deadline.plusSeconds(1)));
    }

    @Test
    void canceledAndDuplicateCheckInsAreRejected() {
        Reservation canceled = reservation(start, start.minusDays(1));
        canceled.cancel();
        assertThrows(IllegalArgumentException.class, () -> canceled.checkIn(start));
        assertFalse(canceled.markNoShow(start.plusMinutes(10)));
        Reservation checkedIn = reservation(start, start.minusDays(1));
        checkedIn.checkIn(start);
        assertThrows(IllegalArgumentException.class, () -> checkedIn.checkIn(start.plusSeconds(1)));
        assertEquals(start, checkedIn.getCheckedInAt());
    }

    @Test
    void latePromotionGetsFiveMinutesFromCreation() {
        Reservation reservation = reservation(start, start.plusMinutes(15));
        assertEquals(start.plusMinutes(20), reservation.getCheckInDeadline());
        assertFalse(reservation.markNoShow(start.plusMinutes(16)));
        reservation.checkIn(start.plusMinutes(19));
        assertEquals(ReservationStatus.CHECKED_IN, reservation.getStatus());
    }

    @Test
    void latePromotionGraceCannotExtendSlotEnd() {
        Reservation reservation = reservation(start, start.plusMinutes(58));
        assertEquals(start.plusHours(1), reservation.getCheckInDeadline());
        assertThrows(IllegalArgumentException.class, () -> reservation.checkIn(start.plusHours(1)));
        assertTrue(reservation.markNoShow(start.plusHours(1)));
    }

    @Test
    void checkInServiceReturnsCheckedInResponse() {
        ReservationRepository repository = mock(ReservationRepository.class);
        ReservationService service = new ReservationService(mock(UserRepository.class),
                mock(RoomRepository.class), repository, mock(ReservationOfferService.class), mock(BookingLocks.class));
        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = reservation(now.minusMinutes(1), now.minusDays(1));
        when(repository.findById(1L)).thenReturn(Optional.of(reservation));
        assertEquals(ReservationStatus.CHECKED_IN, service.checkInReservation(1L, 1L).status());
        assertNotNull(reservation.getCheckedInAt());
        assertThrows(IllegalArgumentException.class, () -> service.checkInReservation(99L, 1L));
    }

    @Test
    void noShowServicePromotesOnceAndLeavesIneligibleReservationsAlone() {
        ReservationRepository repository = mock(ReservationRepository.class);
        ReservationOfferService offers = mock(ReservationOfferService.class);
        ReservationService service = new ReservationService(mock(UserRepository.class),
                mock(RoomRepository.class), repository, offers, mock(BookingLocks.class));
        LocalDateTime now = LocalDateTime.now();
        Reservation overdue = reservation(now.minusMinutes(6), now.minusDays(1));
        Reservation recent = reservation(now.minusMinutes(6), now.minusMinutes(1));
        Reservation checkedIn = reservation(now.minusMinutes(6), now.minusDays(1));
        checkedIn.checkIn(now.minusMinutes(5));
        Reservation canceled = reservation(now.minusMinutes(6), now.minusDays(1));
        canceled.cancel();
        List<Reservation> items = List.of(overdue, recent, checkedIn, canceled);
        for (int i = 0; i < items.size(); i++) {
            when(repository.findById((long) i + 1)).thenReturn(Optional.of(items.get(i)));
        }
        for (long id = 1; id <= 4; id++) {
            service.processDueReservation(id);
            service.processDueReservation(id);
        }

        assertEquals(ReservationStatus.NO_SHOW, overdue.getStatus());
        assertEquals(ReservationStatus.CONFIRMED, recent.getStatus());
        assertEquals(ReservationStatus.CHECKED_IN, checkedIn.getStatus());
        assertEquals(ReservationStatus.CANCELED, canceled.getStatus());
        verify(offers, times(1)).offerToNextWaitingUser(
                overdue.getRoom(), overdue.getStartAt(), overdue.getEndAt());
        verifyNoMoreInteractions(offers);
    }
}

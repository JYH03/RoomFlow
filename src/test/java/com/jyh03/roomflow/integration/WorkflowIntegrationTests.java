package com.jyh03.roomflow.integration;

import com.jyh03.roomflow.domain.reservation.*;
import com.jyh03.roomflow.domain.reservationoffer.*;
import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.domain.user.User;
import com.jyh03.roomflow.domain.waitingqueue.*;
import com.jyh03.roomflow.dto.reservation.*;
import com.jyh03.roomflow.dto.waitingqueue.*;
import com.jyh03.roomflow.global.error.BookingConflictException;
import com.jyh03.roomflow.repository.*;
import com.jyh03.roomflow.service.*;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowIntegrationTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { IsolatedDatabase.configure(registry); }

    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired ReservationRepository reservations;
    @Autowired WaitingQueueRepository queues;
    @Autowired ReservationOfferRepository offers;
    @Autowired ReservationService reservationService;
    @Autowired WaitingQueueService queueService;
    @Autowired ReservationOfferService offerService;
    @Autowired RoomService roomService;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired WebApplicationContext context;
    List<User> people;
    Room room, secondRoom;
    LocalDateTime start;

    @BeforeEach
    void fixtures() {
        // Both supported modes use a database created by this test class.
        offers.deleteAllInBatch();
        queues.deleteAllInBatch();
        reservations.deleteAllInBatch();
        rooms.deleteAllInBatch();
        users.deleteAllInBatch();
        people = IntStream.range(0, 12).mapToObj(i ->
                users.saveAndFlush(new User("test" + i + "@example.com", "test-only-unused-hash", "User " + i))).toList();
        room = rooms.saveAndFlush(new Room(1, "Room 1"));
        secondRoom = rooms.saveAndFlush(new Room(2, "Room 2"));
        start = LocalDate.now().plusDays(2).atTime(14, 0);
    }

    @AfterAll
    void cleanup() {
        if (Boolean.getBoolean("roomflow.test.mysql")) {
            if (dataSource instanceof HikariDataSource pool) pool.close();
            IsolatedDatabase.cleanup();
        }
    }

    ReservationResponse book(int person, Room target) {
        return reservationService.createReservation(people.get(person).getId(),
                new CreateReservationRequest(target.getId(), start, start.plusHours(1)));
    }
    WaitingQueueResponse join(int person) {
        return queueService.joinWaitingQueue(people.get(person).getId(),
                new CreateWaitingQueueRequest(room.getId(), start, start.plusHours(1)));
    }
    ReservationOffer pending() {
        return offers.findAll().stream().filter(o -> o.getStatus() == ReservationOfferStatus.PENDING)
                .findFirst().orElseThrow();
    }
    void release(ReservationResponse reservation) {
        reservationService.cancelReservation(reservation.id(), people.get(0).getId());
    }
    long activeReservations() {
        return reservations.findAll().stream().filter(r -> r.getStatus() == ReservationStatus.CONFIRMED
                || r.getStatus() == ReservationStatus.CHECKED_IN).count();
    }
    long pendingOffers() {
        return offers.findAll().stream().filter(o -> o.getStatus() == ReservationOfferStatus.PENDING).count();
    }
    int race(int count, IntConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count), go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out.");
                    try { action.accept(index); return true; }
                    catch (BookingConflictException expected) { return false; }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get(30, TimeUnit.SECONDS)) successes++;
            return successes;
        } finally {
            go.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void simultaneousBookingsAllowOnlyOneTeamPerRoom() throws Exception {
        assertEquals(1, race(10, i -> book(i, room)));
        assertEquals(1, activeReservations());
    }

    @Test
    void simultaneousBookingsPreventOneUserBookingDifferentRooms() throws Exception {
        assertEquals(1, race(2, i -> book(0, i == 0 ? room : secondRoom)));
        assertEquals(1, activeReservations());
    }

    @Test
    void simultaneousQueueEntriesReceiveUniqueMonotonicOrder() throws Exception {
        book(0, room);
        assertEquals(10, race(10, i -> join(i + 1)));
        assertEquals(IntStream.rangeClosed(1, 10).boxed().toList(),
                queues.findAll().stream().map(WaitingQueue::getQueueOrder).sorted().toList());
    }

    @Test
    void simultaneousDuplicateQueueJoinSucceedsOnce() throws Exception {
        book(0, room);
        assertEquals(1, race(6, i -> join(1)));
        assertEquals(1, queues.count());
    }

    @Test
    void cancelHistoryAllowsRebookingAndQueueReentry() {
        var initial = book(0, room);
        var entry = join(1);
        queueService.cancelWaitingQueue(entry.id(), people.get(1).getId());
        assertEquals(2, join(1).queueOrder());
        release(initial);
        var offer = pending();
        var newReservation = offerService.acceptOffer(offer.getId(), people.get(1).getId());
        assertEquals(ReservationStatus.CONFIRMED, newReservation.status());
        assertEquals(2, reservations.count());
        assertEquals(1, activeReservations());
    }

    @Test
    void pendingOfferBlocksDirectBookingAndAvailability() {
        var initial = book(0, room);
        join(1);
        release(initial);
        assertThrows(BookingConflictException.class, () -> book(2, room));
        assertFalse(roomService.getAvailability(room.getId(), start.toLocalDate()).slots().stream()
                .filter(slot -> slot.startAt().equals(start)).findFirst().orElseThrow().available());
        assertEquals(0, activeReservations());
        assertEquals(1, pendingOffers());
    }

    @Test
    void expiredUnprocessedOfferStillProtectsQueuePriority() {
        var initial = book(0, room);
        join(1);
        join(2);
        release(initial);
        long offerId = pending().getId();
        jdbc.update("UPDATE reservation_offers SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), offerId);
        assertThrows(BookingConflictException.class, () -> book(3, room));
        assertThrows(BookingConflictException.class, () -> offerService.acceptOffer(offerId, people.get(1).getId()));
        offerService.expireOffer(offerId);
        assertEquals(ReservationOfferStatus.EXPIRED, offers.findById(offerId).orElseThrow().getStatus());
        assertEquals(1, pendingOffers());
        assertEquals(1, offerService.getMyOffers(people.get(2).getId()).size());
    }

    @Test
    void simultaneousCancellationPromotesOnlyOnce() throws Exception {
        var initial = book(0, room);
        join(1);
        assertEquals(1, race(5, i -> release(initial)));
        assertEquals(1, pendingOffers());
        assertEquals(1, offers.count());
    }

    @Test
    void simultaneousAcceptCreatesOneReservation() throws Exception {
        var initial = book(0, room);
        join(1);
        release(initial);
        long id = pending().getId();
        assertEquals(1, race(6, i -> offerService.acceptOffer(id, people.get(1).getId())));
        assertEquals(1, activeReservations());
        assertEquals(0, pendingOffers());
    }

    @Test
    void acceptVersusDeclineHasExactlyOneWinner() throws Exception {
        var initial = book(0, room);
        join(1);
        join(2);
        release(initial);
        long id = pending().getId(), userId = people.get(1).getId();
        assertEquals(1, race(2, i -> {
            if (i == 0) offerService.acceptOffer(id, userId);
            else offerService.declineOffer(id, userId);
        }));
        assertEquals(1, activeReservations() + pendingOffers());
        assertNotEquals(ReservationOfferStatus.PENDING, offers.findById(id).orElseThrow().getStatus());
    }

    @Test
    void acceptVersusQueueCancelCannotLeaveReservationAndNextOffer() throws Exception {
        var initial = book(0, room);
        var queue = join(1);
        join(2);
        release(initial);
        long id = pending().getId(), userId = people.get(1).getId();
        assertEquals(1, race(2, i -> {
            if (i == 0) offerService.acceptOffer(id, userId);
            else queueService.cancelWaitingQueue(queue.id(), userId);
        }));
        assertEquals(1, activeReservations() + pendingOffers());
    }

    @Test
    void concurrentExpiryWorkersAreIdempotent() throws Exception {
        var initial = book(0, room);
        join(1); join(2);
        release(initial);
        long id = pending().getId();
        jdbc.update("UPDATE reservation_offers SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), id);
        assertEquals(5, race(5, i -> offerService.expireOffer(id)));
        assertEquals(2, offers.count());
        assertEquals(1, pendingOffers());
    }

    @Test
    void declineAndOfferedQueueCancelAdvanceImmediately() {
        var initial = book(0, room);
        join(1);
        var second = join(2);
        join(3);
        release(initial);
        offerService.declineOffer(pending().getId(), people.get(1).getId());
        assertEquals(1L, queueService.getMyWaitingQueues(people.get(2).getId()).get(0).position());
        queueService.cancelWaitingQueue(second.id(), people.get(2).getId());
        assertEquals(1, offerService.getMyOffers(people.get(3).getId()).size());
        offerService.acceptOffer(pending().getId(), people.get(3).getId());
        assertEquals(1, activeReservations());
        assertEquals(0, pendingOffers());
    }

    @Test
    void usersWhoBookedElsewhereAreSkippedDuringPromotion() {
        var initial = book(0, room);
        var first = join(1);
        join(2);
        book(1, secondRoom);
        release(initial);
        assertEquals(WaitingQueueStatus.EXPIRED, queues.findById(first.id()).orElseThrow().getStatus());
        assertEquals(1, offerService.getMyOffers(people.get(2).getId()).size());
    }

    @Test
    void noShowWorkersPromoteOnceAndCheckedInReservationCompletes() throws Exception {
        var initial = book(0, room);
        var first = join(1);
        var pastStart = LocalDateTime.now().minusMinutes(6);
        jdbc.update("UPDATE reservations SET start_at=?, end_at=?, created_at=? WHERE id=?",
                pastStart, pastStart.plusHours(1), pastStart.minusDays(1), initial.id());
        jdbc.update("UPDATE waiting_queues SET start_at=?, end_at=? WHERE id=?",
                pastStart, pastStart.plusHours(1), first.id());
        assertEquals(4, race(4, i -> reservationService.processDueReservation(initial.id())));
        assertEquals(ReservationStatus.NO_SHOW, reservations.findById(initial.id()).orElseThrow().getStatus());
        assertEquals(1, offers.count());
        var promoted = offerService.acceptOffer(pending().getId(), people.get(1).getId());
        reservationService.checkInReservation(promoted.id(), people.get(1).getId());
        jdbc.update("UPDATE reservations SET start_at=?, end_at=? WHERE id=?",
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1), promoted.id());
        reservationService.processDueReservation(promoted.id());
        assertEquals(ReservationStatus.COMPLETED, reservations.findById(promoted.id()).orElseThrow().getStatus());
    }

    @Test
    void checkInAndMaintenanceDoNotOverwriteEachOther() throws Exception {
        var initial = book(0, room);
        var now = LocalDateTime.now();
        jdbc.update("UPDATE reservations SET start_at=?, end_at=?, created_at=? WHERE id=?",
                now.minusMinutes(1), now.plusMinutes(59), now.minusDays(1), initial.id());
        assertEquals(2, race(2, i -> {
            if (i == 0) reservationService.checkInReservation(initial.id(), people.get(0).getId());
            else reservationService.processDueReservation(initial.id());
        }));
        assertEquals(ReservationStatus.CHECKED_IN, reservations.findById(initial.id()).orElseThrow().getStatus());
    }

    @Test
    void validatesExactHourFutureAndOpeningHours() {
        for (LocalDateTime invalid : List.of(start.plusMinutes(1), start.withHour(8), start.withHour(22),
                LocalDate.now().minusDays(1).atTime(14, 0))) {
            assertThrows(IllegalArgumentException.class, () -> reservationService.createReservation(people.get(0).getId(),
                    new CreateReservationRequest(room.getId(), invalid, invalid.plusHours(1))));
        }
        assertThrows(IllegalArgumentException.class, () -> reservationService.createReservation(people.get(0).getId(),
                new CreateReservationRequest(room.getId(), start, start.plusMinutes(90))));
        var late = start.withHour(21);
        assertEquals(ReservationStatus.CONFIRMED, reservationService.createReservation(people.get(0).getId(),
                new CreateReservationRequest(room.getId(), late, late.plusHours(1))).status());
    }

    @Test
    void openApiIsPublicAndDocumentsProtectedOperations() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.paths['/api/reservation-offers/{reservationOfferId}/decline']").exists())
                .andExpect(jsonPath("$.paths['/api/reservations'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].bearerAuth").isArray());
        mvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void endedQueuesAreExpiredAndSchedulerQueriesSelectDueIds() {
        var initial = book(0, room);
        var first = join(1);
        var second = join(2);
        release(initial);
        var now = LocalDateTime.now();
        jdbc.update("UPDATE waiting_queues SET start_at=?, end_at=?",
                now.minusHours(2), now.minusHours(1));
        var ended = queues.findEndedIds(now, List.of(WaitingQueueStatus.WAITING, WaitingQueueStatus.OFFERED),
                org.springframework.data.domain.PageRequest.of(0, 100));
        assertEquals(2, ended.size());
        ended.forEach(queueService::expireEndedQueue);
        ended.forEach(queueService::expireEndedQueue);
        assertEquals(WaitingQueueStatus.EXPIRED, queues.findById(first.id()).orElseThrow().getStatus());
        assertEquals(WaitingQueueStatus.EXPIRED, queues.findById(second.id()).orElseThrow().getStatus());
        assertEquals(0, pendingOffers());

        var next = book(3, secondRoom);
        jdbc.update("UPDATE reservations SET start_at=?, end_at=?, created_at=? WHERE id=?",
                now.minusMinutes(6), now.plusMinutes(54), now.minusDays(1), next.id());
        assertEquals(List.of(next.id()), reservations.findDueIds(now.minusMinutes(5), now,
                org.springframework.data.domain.PageRequest.of(0, 100)));
    }

    @Test
    void simultaneousAcceptInDifferentRoomsStillPreventsUserDoubleBooking() throws Exception {
        var first = book(0, room);
        var second = book(2, secondRoom);
        join(1);
        queueService.joinWaitingQueue(people.get(1).getId(),
                new CreateWaitingQueueRequest(secondRoom.getId(), start, start.plusHours(1)));
        release(first);
        reservationService.cancelReservation(second.id(), people.get(2).getId());
        var ids = offerService.getMyOffers(people.get(1).getId()).stream().map(o -> o.id()).toList();
        assertEquals(2, ids.size());
        assertEquals(1, race(2, i -> offerService.acceptOffer(ids.get(i), people.get(1).getId())));
        assertEquals(1, activeReservations());
    }

    @Test
    void fullHttpSignupLoginAndBookingUsesRealDatabase() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String signup = "{\"email\":\"http@example.com\",\"password\":\"TestPass123!\",\"name\":\"HTTP Tester\"}";
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(signup))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        String login = mvc.perform(post("/api/auth/login").contentType("application/json").content(signup))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = new tools.jackson.databind.json.JsonMapper().readTree(login).get("accessToken").asText();
        String body = "{\"roomId\":" + room.getId() + ",\"startAt\":\"" + start
                + "\",\"endAt\":\"" + start.plusHours(1) + "\"}";
        mvc.perform(post("/api/reservations").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/reservations").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/reservations?userId=999").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
}

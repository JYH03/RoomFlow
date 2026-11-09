package com.jyh03.roomflow.security;

import com.jyh03.roomflow.HealthController;
import com.jyh03.roomflow.controller.*;
import com.jyh03.roomflow.domain.reservation.Reservation;
import com.jyh03.roomflow.domain.reservation.ReservationStatus;
import com.jyh03.roomflow.domain.reservationoffer.*;
import com.jyh03.roomflow.domain.room.Room;
import com.jyh03.roomflow.domain.user.User;
import com.jyh03.roomflow.domain.waitingqueue.*;
import com.jyh03.roomflow.global.error.GlobalExceptionHandler;
import com.jyh03.roomflow.repository.*;
import com.jyh03.roomflow.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Real controllers, services, security filters and JWT cryptography; repositories are in-memory mocks.
// No local MySQL data or scheduler is touched by this suite.
@SpringJUnitConfig(AuthSecurityTests.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = "roomflow.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class AuthSecurityTests {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtConfig.class, SecurityErrorHandler.class,
            GlobalExceptionHandler.class, AuthController.class, AuthService.class,
            ReservationController.class, ReservationService.class,
            WaitingQueueController.class, WaitingQueueService.class,
            ReservationOfferController.class, ReservationOfferService.class,
            RoomController.class, RoomService.class, HealthController.class, BookingLocks.class})
    static class TestConfig {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean RoomRepository rooms() { return mock(RoomRepository.class); }
        @Bean ReservationRepository reservations() { return mock(ReservationRepository.class); }
        @Bean WaitingQueueRepository queues() { return mock(WaitingQueueRepository.class); }
        @Bean ReservationOfferRepository offers() { return mock(ReservationOfferRepository.class); }
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().findAndAddModules().build(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired ReservationRepository reservations;
    @Autowired WaitingQueueRepository queues;
    @Autowired ReservationOfferRepository offers;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired ObjectMapper mapper;

    MockMvc mvc;
    Map<String, User> userStore;
    Reservation ownedReservation;
    WaitingQueue ownedQueue;
    ReservationOffer ownedOffer;
    static final String PASSWORD = "TestPass123!";

    @BeforeEach
    void setUp() {
        reset(users, rooms, reservations, queues, offers);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userStore = new HashMap<>();
        String hash = passwords.encode(PASSWORD);
        User alice = new User("alice@example.com", hash, "Alice");
        User bob = new User("bob@example.com", hash, "Bob");
        ReflectionTestUtils.setField(alice, "id", 1L);
        ReflectionTestUtils.setField(bob, "id", 2L);
        userStore.put(alice.getEmail(), alice);
        userStore.put(bob.getEmail(), bob);
        when(users.existsByEmail(anyString())).thenAnswer(i -> userStore.containsKey(i.getArgument(0)));
        when(users.findByEmail(anyString())).thenAnswer(i -> Optional.ofNullable(userStore.get(i.getArgument(0))));
        when(users.findById(anyLong())).thenAnswer(i -> userStore.values().stream()
                .filter(u -> u.getId().equals(i.getArgument(0))).findFirst());
        when(users.lockById(anyLong())).thenAnswer(i -> userStore.values().stream()
                .filter(u -> u.getId().equals(i.getArgument(0))).findFirst());
        when(users.existsById(anyLong())).thenAnswer(i -> userStore.values().stream()
                .anyMatch(u -> u.getId().equals(i.getArgument(0))));
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> {
            User user = i.getArgument(0);
            ReflectionTestUtils.setField(user, "id", (long) userStore.size() + 1);
            userStore.put(user.getEmail(), user);
            return user;
        });
        Room room = new Room(1, "Meeting room 1");
        ReflectionTestUtils.setField(room, "id", 1L);
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        when(rooms.lockById(1L)).thenReturn(Optional.of(room));
        when(reservations.findRoomId(10L)).thenReturn(Optional.of(1L));
        when(queues.findRoomId(20L)).thenReturn(Optional.of(1L));
        when(offers.findRoomId(30L)).thenReturn(Optional.of(1L));
        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        ownedReservation = new Reservation(alice, room, start, start.plusHours(1));
        ReflectionTestUtils.setField(ownedReservation, "id", 10L);
        ReflectionTestUtils.setField(ownedReservation, "createdAt", start.minusDays(1));
        when(reservations.findById(10L)).thenReturn(Optional.of(ownedReservation));
        when(reservations.save(any(Reservation.class))).thenAnswer(i -> {
            Reservation reservation = i.getArgument(0);
            ReflectionTestUtils.setField(reservation, "id", 11L);
            ReflectionTestUtils.setField(reservation, "createdAt", LocalDateTime.now());
            return reservation;
        });
        ownedQueue = new WaitingQueue(alice, room, start.plusDays(1), start.plusDays(1).plusHours(1), 1);
        ReflectionTestUtils.setField(ownedQueue, "id", 20L);
        ownedQueue.offer();
        ownedOffer = new ReservationOffer(ownedQueue, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(ownedOffer, "id", 30L);
        when(queues.findById(20L)).thenReturn(Optional.of(ownedQueue));
        when(offers.findById(30L)).thenReturn(Optional.of(ownedOffer));
        when(queues.save(any(WaitingQueue.class))).thenAnswer(i -> {
            WaitingQueue queue = i.getArgument(0);
            ReflectionTestUtils.setField(queue, "id", 21L);
            return queue;
        });
    }

    String token(Long userId) {
        return customToken(userId.toString(), JwtConfig.ISSUER, JwtConfig.AUDIENCE, Instant.now().plusSeconds(3600));
    }

    String customToken(String subject, String issuer, String audience, Instant expiry) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(subject)
                .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(120))
                .notBefore(Instant.now().minusSeconds(120)).expiresAt(expiry).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    String reservationBody(boolean spoofUser) {
        LocalDateTime start = java.time.LocalDate.now().plusDays(2).atTime(14, 0);
        return "{\"roomId\":1,\"startAt\":\"" + start + "\",\"endAt\":\"" + start.plusHours(1)
                + "\"" + (spoofUser ? ",\"userId\":2" : "") + "}";
    }

    @Test
    void publicEndpointsWorkAndPrivateEndpointRequiresToken() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(get("/api/rooms")).andExpect(status().isOk());
        mvc.perform(get("/api/waiting-queues")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(reservationBody(false)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupHashesPasswordAndLoginTokenWorksForMe() throws Exception {
        String signup = mapper.writeValueAsString(Map.of("email", "NEW@example.com", "password", PASSWORD, "name", "New"));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
        User created = userStore.get("new@example.com");
        assertNotEquals(PASSWORD, created.getPasswordHash());
        assertTrue(passwords.matches(PASSWORD, created.getPasswordHash()));
        String result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "NEW@example.com", "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        String accessToken = mapper.readTree(result).get("accessToken").asText();
        assertEquals(created.getId().toString(), decoder.decode(accessToken).getSubject());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateSignupAndConcurrentDuplicateReturnConflict() throws Exception {
        String body = mapper.writeValueAsString(Map.of("email", "ALICE@example.com", "password", PASSWORD, "name", "Alice"));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate email"));
        body = mapper.writeValueAsString(Map.of("email", "new@example.com", "password", PASSWORD, "name", "New"));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordAndUnknownUserReturnSameError() throws Exception {
        String[] emails = {"alice@example.com", "missing@example.com"};
        for (String email : emails) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("email", email, "password", "WrongPass123!"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
        }
    }

    @Test
    void invalidSignupAndUtf8PasswordOver72BytesAreRejected() throws Exception {
        for (String password : List.of("short", "가".repeat(25))) {
            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("email", "new@example.com", "password", password, "name", "New"))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void expiredWrongIssuerWrongAudienceAndBadSubjectTokensAreRejected() throws Exception {
        Instant future = Instant.now().plusSeconds(3600);
        for (String token : List.of(
                customToken("1", JwtConfig.ISSUER, JwtConfig.AUDIENCE, Instant.now().minusSeconds(1)),
                customToken("1", "other", JwtConfig.AUDIENCE, future),
                customToken("1", JwtConfig.ISSUER, "other", future),
                customToken("invalid", JwtConfig.ISSUER, JwtConfig.AUDIENCE, future),
                "not-a-token")) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        String valid = token(1L);
        int signatureStart = valid.lastIndexOf('.') + 1;
        char replacement = valid.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = valid.substring(0, signatureStart) + replacement + valid.substring(signatureStart + 1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reservationOwnerAlwaysComesFromToken() throws Exception {
        mvc.perform(post("/api/reservations").header("Authorization", "Bearer " + token(1L))
                        .contentType(MediaType.APPLICATION_JSON).content(reservationBody(true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void waitingQueueOwnerAlwaysComesFromToken() throws Exception {
        when(reservations.existsByRoomIdAndStartAtLessThanAndEndAtGreaterThanAndStatusIn(
                eq(1L), any(), any(), anyCollection())).thenReturn(true);
        mvc.perform(post("/api/waiting-queues").header("Authorization", "Bearer " + token(1L))
                        .contentType(MediaType.APPLICATION_JSON).content(reservationBody(true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void waitingQueueQueryCannotSelectOtherUser() throws Exception {
        mvc.perform(get("/api/waiting-queues?userId=2").header("Authorization", "Bearer " + token(1L)))
                .andExpect(status().isOk());
        verify(queues).findByUserIdAndStatusInOrderByCreatedAtDesc(eq(1L), anyCollection());
        verify(queues, never()).findByUserIdAndStatusInOrderByCreatedAtDesc(eq(2L), anyCollection());
    }

    @Test
    void otherUserCannotCancelOrCheckInReservation() throws Exception {
        for (String action : List.of("cancel", "check-in")) {
            mvc.perform(post("/api/reservations/10/" + action).header("Authorization", "Bearer " + token(2L)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        }
        assertEquals(ReservationStatus.CONFIRMED, ownedReservation.getStatus());
        assertNull(ownedReservation.getCheckedInAt());
    }

    @Test
    void otherUserCannotCancelQueueOrAcceptOffer() throws Exception {
        mvc.perform(post("/api/waiting-queues/20/cancel").header("Authorization", "Bearer " + token(2L)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reservation-offers/30/accept").header("Authorization", "Bearer " + token(2L)))
                .andExpect(status().isForbidden());
        assertEquals(WaitingQueueStatus.OFFERED, ownedQueue.getStatus());
        assertEquals(ReservationOfferStatus.PENDING, ownedOffer.getStatus());
        verify(reservations, never()).save(any());
    }

    @Test
    void ownerCanCheckIn() throws Exception {
        mvc.perform(post("/api/reservations/10/check-in").header("Authorization", "Bearer " + token(1L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHECKED_IN"));
    }

    @Test
    void ownerCanCancelReservation() throws Exception {
        mvc.perform(post("/api/reservations/10/cancel").header("Authorization", "Bearer " + token(1L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void ownerCanCancelQueue() throws Exception {
        mvc.perform(post("/api/waiting-queues/20/cancel").header("Authorization", "Bearer " + token(1L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void ownerCanAcceptOffer() throws Exception {
        mvc.perform(post("/api/reservation-offers/30/accept").header("Authorization", "Bearer " + token(1L)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(1));
        assertEquals(ReservationOfferStatus.ACCEPTED, ownedOffer.getStatus());
    }

    @Test
    void missingOrWeakSecretIsRejected() {
        JwtConfig config = new JwtConfig();
        assertThrows(IllegalStateException.class, () -> config.jwtSecretKey(""));
        assertThrows(IllegalStateException.class, () -> config.jwtSecretKey("bad!"));
        assertThrows(IllegalStateException.class, () -> config.jwtSecretKey("dGVzdA=="));
    }
}

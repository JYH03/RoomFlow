# API Specification

## Room

| Method | Endpoint | Description |
|---|---|---|
| GET | /rooms | 미팅룸 1실~13실 목록 조회 |
| GET | /rooms/{roomId}/availability | 날짜별 예약 가능 시간 조회 |

## Reservation

| Method | Endpoint | Description |
|---|---|---|
| POST | /reservations | 미팅룸 예약 |
| GET | /reservations/me | 내 예약 목록 조회 |
| POST | /reservations/{reservationId}/cancel | 예약 취소 |
| POST | /reservations/{reservationId}/check-in | 체크인 |

## Waiting Queue

| Method | Endpoint | Description |
|---|---|---|
| POST | /waiting-queues | 대기열 등록 |
| GET | /waiting-queues/me | 내 대기열·대기 순번 조회 |
| POST | /offers/{offerId}/accept | 예약 제안 수락 |
| POST | /offers/{offerId}/reject | 예약 제안 거절 |
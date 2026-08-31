# RoomFlow API 명세

> 현재는 인증 기능 전 단계이므로, 요청에 `userId`를 직접 포함합니다.

## 공통

- Base URL: `http://localhost:8080`
- Content-Type: `application/json`
- 시간 형식: `yyyy-MM-dd'T'HH:mm:ss`
  - 예: `2026-08-31T14:00:00`

## 1. 미팅룸 목록 조회

```http
GET /api/rooms
```

### 응답 예시: 200 OK

```json
[
  {
    "id": 1,
    "roomNumber": 1,
    "name": "미팅룸 1실",
    "active": true
  }
]
```

---

## 2. 미팅룸 시간대별 예약 가능 여부 조회

```http
GET /api/rooms/{roomId}/availability?date={date}
```

### 요청 예시

```http
GET /api/rooms/1/availability?date=2026-08-31
```

### 응답 예시: 200 OK

```json
{
  "roomId": 1,
  "date": "2026-08-31",
  "slots": [
    {
      "startAt": "2026-08-31T09:00:00",
      "endAt": "2026-08-31T10:00:00",
      "available": true
    },
    {
      "startAt": "2026-08-31T14:00:00",
      "endAt": "2026-08-31T15:00:00",
      "available": false
    }
  ]
}
```

- 운영 시간은 `09:00 ~ 22:00`입니다.
- 한 슬롯은 1시간입니다.
- `CONFIRMED`, `CHECKED_IN` 상태의 예약이 있으면 해당 시간대는 `available: false`입니다.

---

## 3. 예약 생성

```http
POST /api/reservations
```

### 요청 본문

```json
{
  "userId": 2,
  "roomId": 1,
  "startAt": "2026-08-31T14:00:00",
  "endAt": "2026-08-31T15:00:00"
}
```

### 응답 예시: 201 Created

```json
{
  "id": 3,
  "userId": 2,
  "roomId": 1,
  "startAt": "2026-08-31T14:00:00",
  "endAt": "2026-08-31T15:00:00",
  "status": "CONFIRMED"
}
```

### 예약 규칙

- 시작 시간과 종료 시간은 필수입니다.
- 예약은 정확히 1시간 단위입니다.
- 같은 미팅룸·시간대에는 활성 예약이 하나만 가능합니다.
- 한 사용자는 겹치는 시간에 중복 예약할 수 없습니다.
- 예약 상태는 처음에 `CONFIRMED`로 생성됩니다.

---

## 4. 예약 취소

```http
POST /api/reservations/{reservationId}/cancel
```

### 요청 예시

```http
POST /api/reservations/3/cancel
```

### 응답 예시: 200 OK

```json
{
  "id": 3,
  "userId": 2,
  "roomId": 1,
  "startAt": "2026-08-31T14:00:00",
  "endAt": "2026-08-31T15:00:00",
  "status": "CANCELED"
}
```

- `CONFIRMED` 상태의 예약만 취소할 수 있습니다.
- 취소된 예약 데이터는 삭제하지 않고 `CANCELED` 상태로 보존합니다.

---

## 5. 대기열 등록

이미 다른 사용자의 활성 예약이 있는 시간대에만 대기열 등록이 가능합니다.

```http
POST /api/waiting-queues
```

### 요청 본문

```json
{
  "userId": 1,
  "roomId": 1,
  "startAt": "2026-08-31T14:00:00",
  "endAt": "2026-08-31T15:00:00"
}
```

### 응답 예시: 201 Created

```json
{
  "id": 2,
  "userId": 1,
  "roomId": 1,
  "startAt": "2026-08-31T14:00:00",
  "endAt": "2026-08-31T15:00:00",
  "queueOrder": 2,
  "status": "WAITING"
}
```

### 대기열 규칙

- 예약 가능한 빈 시간대에는 대기열 대신 예약해야 합니다.
- 이미 해당 시간에 예약이 있는 사용자는 대기열에 등록할 수 없습니다.
- 같은 사용자는 동일한 방·시간대의 활성 대기열에 중복 등록할 수 없습니다.
- 취소한 대기열 기록은 남으며, 재등록하면 새 순번을 받습니다.
- 최초 등록 상태는 `WAITING`입니다.

---

## 6. 내 대기열 목록 조회

```http
GET /api/waiting-queues?userId={userId}
```

### 요청 예시

```http
GET /api/waiting-queues?userId=1
```

### 응답 예시: 200 OK

```json
[
  {
    "id": 2,
    "userId": 1,
    "roomId": 1,
    "startAt": "2026-08-31T14:00:00",
    "endAt": "2026-08-31T15:00:00",
    "queueOrder": 2,
    "status": "WAITING"
  }
]
```

- `WAITING`, `OFFERED` 상태의 대기열만 반환합니다.
- 조회할 활성 대기열이 없으면 빈 배열 `[]`을 반환합니다.

---

## 7. 대기열 취소

```http
POST /api/waiting-queues/{waitingQueueId}/cancel
```

### 요청 예시

```http
POST /api/waiting-queues/2/cancel
```

### 응답 예시: 200 OK

```json
{
  "id": 2,
  "userId": 1,
  "roomId": 1,
  "startAt": "2026-08-31T14:00:00",
  "endAt": "2026-08-31T15:00:00",
  "queueOrder": 2,
  "status": "CANCELED"
}
```

- `WAITING`, `OFFERED` 상태의 대기열만 취소할 수 있습니다.
- 취소된 대기열은 삭제하지 않고 `CANCELED` 상태로 보존합니다.

---

## 예약 상태

| 상태 | 의미 |
| --- | --- |
| `CONFIRMED` | 예약 완료 |
| `CHECKED_IN` | 이용 시작 처리 완료 |
| `CANCELED` | 예약 취소 |
| `NO_SHOW` | 노쇼 처리 |
| `COMPLETED` | 이용 완료 |

## 대기열 상태

| 상태 | 의미 |
| --- | --- |
| `WAITING` | 대기 중 |
| `OFFERED` | 예약 승급 제안 상태 |
| `ACCEPTED` | 승급 제안 수락 |
| `EXPIRED` | 승급 제안 만료 |
| `CANCELED` | 대기열 취소 |

## 오류 응답

비즈니스 규칙 또는 입력값 검증에 실패하면 다음과 같은 형식으로 응답합니다.

```json
{
  "status": 400,
  "message": "이미 해당 시간대에 예약이 있어 대기열에 등록할 수 없습니다.",
  "timestamp": "2026-08-31T15:00:00"
}
```

| 상태 코드 | 의미 |
| --- | --- |
| `200 OK` | 조회·취소 성공 |
| `201 Created` | 예약 또는 대기열 등록 성공 |
| `400 Bad Request` | 잘못된 요청, 입력값 누락 또는 비즈니스 규칙 위반 |
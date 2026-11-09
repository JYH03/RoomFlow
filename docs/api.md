# RoomFlow API — Day 14

## 공통

- Base URL: `http://localhost:8080`, JSON
- 보호 API: `Authorization: Bearer <accessToken>`
- 사용자 ID는 JWT에서 결정합니다. 본문·쿼리의 `userId`로 타인을 지정할 수 없습니다.
- 시간: 한국 시간의 `yyyy-MM-dd'T'HH:mm:ss` (UTC 오프셋 없는 LocalDateTime)
- 모든 신규 예약·대기는 미래 정시부터 정확히 1시간. 운영 시간 09:00~22:00.
- 아래 날짜는 형식 예시입니다. 실행 시 미래 날짜로 바꾸세요.
- Swagger: `/swagger-ui/index.html`, OpenAPI: `/v3/api-docs`
- 보호 자원 변경은 소유자만 가능합니다.

## 엔드포인트

| Method | 경로 | 인증 | 성공 | 기능 |
|---|---|---|---|---|
| GET | /health | 공개 | 200 | 서버 응답 확인 |
| POST | /api/auth/signup | 공개 | 201 | 회원가입 |
| POST | /api/auth/login | 공개 | 200 | JWT 발급 |
| GET | /api/auth/me | 필요 | 200 | 내 정보 |
| GET | /api/rooms | 공개 | 200 | 방 번호순 목록 |
| GET | /api/rooms/{roomId}/availability?date=yyyy-MM-dd | 공개 | 200 | 하루 13개 슬롯 |
| POST | /api/reservations | 필요 | 201 | 예약 생성 |
| GET | /api/reservations | 필요 | 200 | 내 전체 예약 이력, 시작 시각 내림차순 |
| POST | /api/reservations/{reservationId}/cancel | 본인 | 200 | 확정 예약 취소 |
| POST | /api/reservations/{reservationId}/check-in | 본인 | 200 | 입실 처리 |
| POST | /api/waiting-queues | 필요 | 201 | 대기 등록 |
| GET | /api/waiting-queues | 필요 | 200 | 내 WAITING/OFFERED 목록 |
| POST | /api/waiting-queues/{waitingQueueId}/cancel | 본인 | 200 | 대기 취소 |
| GET | /api/reservation-offers | 필요 | 200 | 내 제안 이력, 생성 시각 내림차순 |
| POST | /api/reservation-offers/{reservationOfferId}/accept | 본인 | 201 | 수락·새 예약 생성 |
| POST | /api/reservation-offers/{reservationOfferId}/decline | 본인 | 200 | 거절·다음 대기자 승급 |

목록이 없으면 `[]`입니다. 취소·체크인·수락·거절은 요청 본문이 없습니다. 경로의 예약 ID, 대기열 ID, 제안 ID를 구분하세요.

## 1. 회원가입·로그인

회원가입 본문:

```json
{"email":"tester@example.com","password":"ExamplePass123!","name":"테스트 사용자"}
```

201 응답:

```json
{"id":4,"email":"tester@example.com","name":"테스트 사용자"}
```

이메일은 소문자로 정규화하며 최대 100자, 이름은 공백이 아닌 최대 50자입니다. 비밀번호는 8~72자이면서 UTF-8 72바이트 이하여야 합니다. BCrypt 해시만 저장하며 응답에는 포함하지 않습니다. 이메일 중복은 409입니다.

로그인은 위 본문에서 email·password만 보냅니다.

```json
{
  "accessToken":"<JWT>",
  "tokenType":"Bearer",
  "expiresIn":3600,
  "user":{"id":4,"email":"tester@example.com","name":"테스트 사용자"}
}
```

잘못된 로그인은 동일한 401 메시지입니다. JWT의 서명·발급자·대상·만료·사용자 ID 형식을 검사합니다. 내 정보는 회원가입 응답과 같은 형식입니다. 갱신 토큰과 서버 측 로그아웃은 없으며 만료 시 다시 로그인합니다.

## 2. 방 조회

목록 응답의 각 항목:

```json
{"id":1,"roomNumber":1,"name":"미팅룸 1실","active":true}
```

예약 가능 여부 응답 (slots 일부만 표시):

```json
{
  "roomId":1,
  "date":"2030-01-02",
  "slots":[
    {"startAt":"2030-01-02T14:00:00","endAt":"2030-01-02T15:00:00","available":false}
  ]
}
```

실제로는 09~22시의 13개 슬롯을 반환합니다. 비활성 방, 이미 시작한 시간, 활성 예약, PENDING 제안과 겹치면 false입니다. 조회 결과는 조회 시점의 정보일 뿐이며, 예약 API에서 잠금 후 다시 검증합니다.

## 3. 예약 생성·조회·취소·체크인

예약 생성 본문:

```json
{"roomId":1,"startAt":"2030-01-02T14:00:00","endAt":"2030-01-02T15:00:00"}
```

예약 응답:

```json
{"id":3,"userId":4,"roomId":1,"startAt":"2030-01-02T14:00:00","endAt":"2030-01-02T15:00:00","status":"CONFIRMED"}
```

- 같은 방·시간 또는 같은 사용자·시간의 활성 예약은 하나만 가능합니다.
- 대기자에게 배정 중인 슬롯은 일반 예약으로 가져갈 수 없습니다.
- 취소는 CONFIRMED만 가능하며 기록을 보존합니다. 다음 대기자가 있으면 같은 트랜잭션에서 제안을 생성합니다.
- 체크인은 CONFIRMED 미입실 예약만 가능합니다. 성공하면 CHECKED_IN, DB에 checked_in_at 기록.
- 체크인 가능 구간은 `시작 이상, min(max(시작, 생성)+5분, 종료) 미만`입니다.
- 시작 후 승급 예약에도 생성부터 최대 5분을 주지만 종료 시간은 늘어나지 않습니다.
- 기한 이후 체크인, 시작 전 체크인, 중복 체크인은 400입니다.

## 4. 대기 등록·조회·취소

등록 본문은 예약 생성과 같습니다. 활성 예약 또는 대기/제안으로 배정 중인 슬롯에만 등록 가능합니다. 같은 시간의 확정 예약을 이미 가진 사용자, 같은 방·시작 시각에 중복 대기 중인 사용자는 409입니다.

대기 응답:

```json
{
  "id":8,"userId":4,"roomId":1,
  "startAt":"2030-01-02T14:00:00","endAt":"2030-01-02T15:00:00",
  "queueOrder":3,"status":"WAITING","position":2
}
```

- queueOrder: 과거 기록까지 포함한 고정 발급 순번. 취소해도 재번호를 매기지 않습니다.
- position: 현재 WAITING/OFFERED 중 내 앞사람 수 + 1. 위 예시는 3번으로 등록했지만 현재 2번째입니다.
- 취소 응답은 CANCELED, position은 null입니다.
- OFFERED 상태에서 취소하면 해당 제안도 DECLINED 처리하고 다음 대기자에게 즉시 제안합니다.
- 이용 시간이 끝난 활성 대기는 스케줄러에서 EXPIRED 처리합니다.

## 5. 제안 조회·수락·거절

조회 목록/거절 응답의 항목:

```json
{
  "id":5,"waitingQueueId":8,"roomId":1,
  "startAt":"2030-01-02T14:00:00","endAt":"2030-01-02T15:00:00",
  "expiresAt":"2030-01-01T12:10:00","status":"PENDING"
}
```

- 수락은 새 예약 응답(201)을 반환합니다. 제안·대기는 ACCEPTED, 새 예약은 CONFIRMED.
- 거절은 제안 응답(200)을 반환합니다. 제안은 DECLINED, 대기는 CANCELED, 다음 대기자에게 즉시 제안.
- PENDING 제안 + OFFERED 대기 + 기한 전 + 이용 종료 전이어야 수락·거절 가능합니다.
- 현재 시각이 expiresAt과 같거나 이후이면 409. 스케줄러 실행 여부와 무관합니다.
- 수락 시 사용자가 다른 방을 이미 예약했는지도 다시 검사합니다.
- 수락·거절을 중복 실행하면 첫 성공 이후 요청은 409입니다.

## 6. 자동 처리

HTTP로 강제 실행하는 관리자 API는 없습니다.

- 노쇼: 기한에 도달한 CONFIRMED 미입실 예약 → NO_SHOW → 다음 대기자 제안.
- 완료: 종료된 CHECKED_IN 예약 → COMPLETED.
- 제안 만료: PENDING → EXPIRED, OFFERED 대기 → EXPIRED → 다음 대기자 제안.
- 대기 종료: 이용 시간이 끝난 WAITING/OFFERED → EXPIRED, 연결된 PENDING도 만료.
- 다음 대기자가 같은 시간에 다른 방을 예약한 경우 해당 대기를 EXPIRED로 건너뜁니다.
- 제안 기한은 생성 시각 + 10분과 이용 종료 시각 중 이른 시각. 시작 시각에서는 자르지 않습니다.
- 만료 시각을 지난 PENDING도 만료 처리가 커밋될 때까지 슬롯을 보호합니다.
- 자동 만료는 응답이 아니므로 responded_at은 null입니다.
- 한 항목의 상태 변경·다음 제안 생성은 하나의 트랜잭션. 실패한 항목은 다음 주기에 재시도합니다.

```properties
roomflow.offer-expiration.enabled=true
roomflow.offer-expiration.delay-ms=30000
roomflow.no-show.enabled=true
roomflow.no-show.delay-ms=30000
```

위 값은 기본값입니다. 스케줄러는 서버 시작 30초 후 시작하며, 실행이 끝난 뒤 30초 후 다시 실행합니다. 각 조회는 최대 100건이라 많은 과거 데이터가 있으면 여러 주기가 필요합니다.

## 상태와 오류

- 예약: CONFIRMED / CHECKED_IN / CANCELED / NO_SHOW / COMPLETED
- 대기: WAITING / OFFERED / ACCEPTED / EXPIRED / CANCELED
- 제안: PENDING / ACCEPTED / DECLINED / EXPIRED

```json
{"status":409,"message":"해당 시간대는 이미 예약되어 있습니다.","timestamp":"2030-01-01T12:00:00"}
```

| 코드 | 의미 |
|---|---|
| 400 | 잘못된 JSON·필수값·시간 정책, 존재하지 않는 대상, 체크인 조건 위반 |
| 401 | 인증 누락·잘못된 로그인·유효하지 않은 토큰 |
| 403 | 다른 사용자의 예약·대기·제안 변경 |
| 409 | 이메일·예약·대기 중복, 제안 기한/상태 충돌, 이미 처리된 취소, 잠금 충돌 |

이 MVP는 존재하지 않는 업무 대상에 400을 사용합니다. 409가 발생하면 현재 예약·제안 상태를 다시 조회하세요. 잠금 충돌만 잠시 후 재시도를 고려하며, 이미 예약된 슬롯을 무조건 반복 요청하지 않습니다.

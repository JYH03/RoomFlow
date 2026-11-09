# Day 11: 체크인과 노쇼 자동 처리

> 당시 개발 기록입니다. Day 14 현재 정책·API는 [최신 API 명세](api.md)와 [최종 안내](day14.md)를 기준으로 합니다. 과거 날짜나 정시가 아닌 신규 예약 예시는 현재 검증 정책에 맞게 변경해야 합니다.

> Day 12 이후에는 아래의 API 요청에 각 사용자로 로그인한 `-Headers $headers`가 필요합니다. 요청 본문의 `userId`는 제거하세요. [인증 테스트 안내](day12.md)

## 정책

- 체크인 API: `POST /api/reservations/{reservationId}/check-in`, 본문 없음.
- 일반 사전 예약은 시작 시각부터 5분 미만까지 체크인 가능. 정확히 5분이 되면 노쇼 대상.
- 승급으로 시작 후 생성된 예약은 생성 시각부터 5분의 여유를 부여. 직접 생성된 늦은 예약에도 동일하게 적용.
- 체크인 기한은 이용 종료 시각을 넘지 않음. 원래 시작·종료 시각은 보존.
- 미입실 예약은 30초 주기의 스케줄러가 `NO_SHOW` 처리하고 다음 대기자에게 제안.
- 종료된 예약에는 새 제안을 만들지 않음. 시작 후 제안의 수락 기한은 현재 + 10분과 이용 종료 중 이른 시각.
- 서버를 재시작하면 예전 미입실 확정 예약도 노쇼 처리될 수 있음.

## 변경 파일

- `Reservation`: 체크인 시간·상태 검사, 체크인 기한 계산, 노쇼 상태 전환.
- `ReservationRepository`: 시작·생성 시각 및 종료 시각을 고려한 노쇼 후보 조회.
- `ReservationService`: 체크인과 노쇼 처리 트랜잭션.
- `ReservationController`: 체크인 API.
- `ReservationNoShowScheduler`: 30초 주기 호출.
- `ReservationOfferService`: 시작 후 노쇼 승급 허용, 종료된 제안 수락 방지.
- 테스트 및 API 문서. DB 테이블 변경은 필요 없음.

## 자동 검증

```powershell
.\gradlew.bat build
```

두 스케줄러는 컨텍스트 테스트에서 비활성화. 단위 테스트는 DB를 변경하지 않음.
체크인 시작·마감 경계, 중복·취소 요청, 승급자의 새 체크인 기한, 종료 제한, 노쇼 재처리 방지, 체크인 사용자 제외, 시작 후 승급·수락을 검증.

## 실제 서버 확인

서버 터미널에서 재시작:

```powershell
.\gradlew.bat bootRun
```

아래는 **다른 PowerShell 터미널**에서 실행. 사용자 ID `1, 2, 3`, 방 ID `12, 13`이 존재하고 테스트 시간에 다른 예약이 없다는 전제. 각 요청이 실패하면 다음 단계로 진행하지 말고 오류 확인.

### A. 체크인 성공

```powershell
$start = (Get-Date).AddMinutes(1)
$body = @{
    userId = 1
    roomId = 12
    startAt = $start.ToString('yyyy-MM-ddTHH:mm:ss')
    endAt = $start.AddHours(1).ToString('yyyy-MM-ddTHH:mm:ss')
} | ConvertTo-Json -Compress

$checkInReservation = Invoke-RestMethod -Uri 'http://localhost:8080/api/reservations' -Method Post -ContentType 'application/json' -Body $body -ErrorAction Stop
$checkInReservation
```

응답의 `startAt` 시각까지 기다린 후, 5분 안에 아래 실행:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/reservations/$($checkInReservation.id)/check-in" -Method Post -ErrorAction Stop
```

`CHECKED_IN` 확인. 같은 요청을 반복하면 400 오류가 정상. 시작 + 5분 이후에도 이 예약은 `NO_SHOW`가 되면 안 됨.

### B. 노쇼와 승급

예약자 2와 대기자 3으로 별도 테스트:

```powershell
$start = (Get-Date).AddMinutes(1)
$request = @{
    userId = 2
    roomId = 13
    startAt = $start.ToString('yyyy-MM-ddTHH:mm:ss')
    endAt = $start.AddHours(1).ToString('yyyy-MM-ddTHH:mm:ss')
}
$body = $request | ConvertTo-Json -Compress
$noShowReservation = Invoke-RestMethod -Uri 'http://localhost:8080/api/reservations' -Method Post -ContentType 'application/json' -Body $body -ErrorAction Stop
$noShowReservation

$request.userId = 3
$body = $request | ConvertTo-Json -Compress
$waiting = Invoke-RestMethod -Uri 'http://localhost:8080/api/waiting-queues' -Method Post -ContentType 'application/json' -Body $body -ErrorAction Stop
$waiting
```

예약자 2는 체크인하지 않고 서버를 켜둠. `startAt + 5분` 이후 약 30초까지 기다린 후 Workbench에서 조회:

```sql
SELECT id, user_id, room_id, start_at, end_at, status, checked_in_at
FROM roomflow.reservations
WHERE room_id IN (12, 13)
ORDER BY id DESC;

SELECT o.id AS offer_id, w.id AS waiting_queue_id, w.user_id,
       w.start_at, w.status AS queue_status, o.status AS offer_status,
       o.expires_at
FROM roomflow.reservation_offers o
JOIN roomflow.waiting_queues w ON w.id = o.waiting_queue_id
WHERE w.room_id = 13
ORDER BY o.id DESC;
```

예약자 2의 예약은 `NO_SHOW`, 대기자 3은 `OFFERED`, 새 제안은 `PENDING`이면 정상.
결과의 실제 `offer_id`를 아래 `123` 대신 입력해 수락 후 체크인:

```powershell
$offerId = 123
$promoted = Invoke-RestMethod -Uri "http://localhost:8080/api/reservation-offers/$offerId/accept" -Method Post -ErrorAction Stop
$promoted
Invoke-RestMethod -Uri "http://localhost:8080/api/reservations/$($promoted.id)/check-in" -Method Post -ErrorAction Stop
```

새 예약이 먼저 `CONFIRMED`, 체크인 후 `CHECKED_IN`이면 시작 후 승급 경로까지 확인한 것.

## 후속 범위

본인 확인은 Day 12에서 구현했습니다. 동시 체크인·노쇼·수락 요청의 잠금 처리는 Day 13에서 진행. 제안 중인 시간대의 일반 예약 선점 방지 역시 아직 필요.

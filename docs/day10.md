# Day 10: 예약 제안 자동 만료

> 당시 개발 기록입니다. Day 14 현재 정책·API는 [최신 API 명세](api.md)와 [최종 안내](day14.md)를 기준으로 합니다. 과거 날짜나 정시가 아닌 신규 예약 예시는 현재 검증 정책에 맞게 변경해야 합니다.

> Day 12 이후에는 이 문서의 API 요청에도 Bearer 인증이 필요합니다. 예약·대기열 요청의 `userId`는 제거하고 해당 사용자로 로그인한 헤더를 사용하세요. [인증 테스트 안내](day12.md)

## 수정한 코드

- `ReservationOfferRepository`: 실제 파일 위치에 맞춰 `domain.reservationoffer` 패키지로 정리. 만료 대상 조회 및 유효한 제안 존재 여부 조회.
- `ReservationOfferService`: 만료 처리, 다음 대기자 제안 생성, 수락 기한 경계 검증.
- `ReservationService`: 예약 취소 시 공통 제안 생성 메서드 호출.
- `ReservationOffer`: 자동 만료 시 사용자 응답 시각을 기록하지 않음.
- `ReservationOfferScheduler`: 별도 서비스 호출로 트랜잭션 적용. 기본 30초 주기.
- `RoomflowApplication`: 기존 `@EnableScheduling` 유지.

`queueOrder`는 등록 순서를 나타내는 고정 번호입니다. 앞사람이 취소돼도 번호를 다시 매기지 않으므로, 현재 내 앞의 대기 인원 수와는 다릅니다.

## 자동 테스트

```powershell
.\gradlew.bat build
```

서비스 단위 테스트는 실제 DB를 사용하지 않습니다. 만료와 다음 승급, 재처리 방지, 대기자 없음, 취소 상태 보존, 지난 시간대, 기존 예약, 기존 유효 제안, 시작 시각으로 기한 제한, 만료 제안 수락 거절, 수락 완료 상태 보존을 검증합니다.

기존 Spring 컨텍스트 테스트는 로컬 MySQL 연결이 필요하지만 스케줄러는 꺼 둡니다.

## 로컬 수동 확인

1. 서버를 재시작합니다 (`.\gradlew.bat bootRun`).
2. 미래 날짜의 동일한 방·시간에 예약자 A, 대기자 B와 C를 준비합니다. 서로 다른 실제 사용자 ID를 사용합니다.
3. A의 예약을 취소합니다. B의 대기열이 `OFFERED`, 제안이 `PENDING`인지 확인합니다.
4. B가 수락하지 않은 채 10분과 다음 스케줄 실행까지 기다립니다.
5. B의 제안·대기열이 `EXPIRED`, C의 대기열이 `OFFERED`, C의 새 제안이 `PENDING`인지 확인합니다.
6. 한 번 더 주기가 지나도 C의 유효 제안이 중복 생성되지 않는지 확인합니다.

Workbench 조회 쿼리:

```sql
SELECT o.id AS offer_id, o.waiting_queue_id, w.user_id,
       w.room_id, w.start_at, w.queue_order,
       w.status AS queue_status, o.status AS offer_status,
       o.expires_at, o.responded_at
FROM roomflow.reservation_offers o
JOIN roomflow.waiting_queues w ON w.id = o.waiting_queue_id
ORDER BY o.id DESC;
```

서버 실행 중에는 기존 `PENDING` 만료 기록도 처리됩니다. Day 11부터 노쇼 복구를 위해 시작 후에도 종료 전이면 다음 대기자를 승급시킵니다. 종료된 시간대에는 새 제안을 만들지 않습니다.

## 남은 범위

- 동시 수락·만료 및 여러 서버 실행에 대한 잠금 처리.
- 제안 응답 대기 중인 시간대의 일반 예약 선점 방지 및 예약 가능 여부 반영.
- 제안 거절·대기열 취소 즉시 다음 승급 (현재 취소된 제안은 기한 만료 시 다음 처리).

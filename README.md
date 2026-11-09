# RoomFlow

학교 도서관 미팅룸을 위한 **예약·대기열·취소 및 노쇼 자동 승급 백엔드**입니다.

## 시작한 이유

학교 중앙도서관 앱의 그룹 스터디룸 예약 기능을 이용하며, 시험 기간에 예약이 빨리 마감되는 문제에 관심을 갖게 되었습니다. 취소되거나 예약자가 오지 않는 시간을 다음 이용자에게 연결하면 어떨까 하는 생각에서 시작했습니다.

실제 학교 앱과 연동한 서비스는 아닙니다. **학교의 13개 미팅룸을 모델로 만든 학습용 프로젝트** 이며 아래 시간·노쇼 정책은 이 프로젝트에서 정한 규칙입니다.

## 구현 범위 — Day 14 MVP

- 1~13실 조회, 날짜별 1시간 슬롯의 예약 가능 여부 조회
- 회원가입·로그인, BCrypt 비밀번호 해시와 1시간짜리 JWT
- 본인 예약 생성·조회·취소·체크인
- 대기열 등록·취소·현재 순위 조회
- 취소·노쇼 시 선착순 승급 제안, 본인 제안 조회·수락·거절
- 제안 만료 후 다음 대기자 승급, 이용 종료·남은 대기열 정리
- DB 비관적 잠금으로 동시 예약·수락·취소·만료 충돌 제어
- Swagger UI, 단위·보안·DB 통합·동시성 테스트

## 예약 정책

| 항목 | 규칙 |
|---|---|
| 팀 | 대표 사용자 계정 1개로 표현. 별도 팀·팀원 관리 없음 |
| 운영 시간 | 09:00~22:00, 한국 시간 기준 |
| 신규 예약·대기 | 미래 정시 시작, 정확히 1시간, 당일 종료 |
| 중복 | 같은 방에 한 팀만, 같은 사용자는 겹치는 시간에 한 방만 |
| 대기 순서 | DB에 등록된 순번 순. 취소 후 재등록 시 뒤에 추가 |
| 제안 기한 | 생성부터 10분, 이용 종료 시각을 넘지 않음 |
| 체크인 | 시작 이상, 체크인 기한 미만 |
| 체크인 기한 | `min(max(시작 시각, 예약 생성 시각) + 5분, 종료 시각)` |
| 자동 처리 | 기본 30초 간격, 작업별 최대 100건씩 건별 트랜잭션 |
| 기록 | 취소·만료 기록은 삭제하지 않고 상태로 보관 |

설계 초안의 1~2시간 예약 중 **현재 MVP는 1시간만 지원**합니다. 시작 후 일반 예약·새 대기 등록은 불가하며, 이미 대기 중인 사람의 승급 수락만 종료 전까지 가능합니다.

제안 중인 슬롯은 일반 예약으로 선점할 수 없습니다. 제안 시각이 지났더라도 스케줄러가 만료와 다음 승급을 처리할 때까지 보호합니다. 실제 스케줄러의 처리 시점은 부하에 따라 기한보다 늦을 수 있지만, 수락·체크인 API는 요청 시각에 기한을 직접 검사합니다.

## 기술 스택과 구조

Java 17 · Spring Boot 4.1.1 · Spring Data JPA/Hibernate · MySQL 8 · Spring Security/JWT · Gradle Wrapper · springdoc OpenAPI · JUnit/Mockito · H2

`Controller → Service(정책·트랜잭션) → Repository → MySQL`

- 엔티티: User, Room, Reservation, WaitingQueue, ReservationOffer
- DTO를 통해 응답하며 비밀번호 해시와 엔티티 자체를 노출하지 않습니다.
- 변경 요청은 사용자 행 → 방 행 순서로 잠급니다. 스케줄러는 방 하나씩만 잠급니다.
- 잠금 이후 최신 커밋을 읽도록 READ_COMMITTED를 사용합니다.
- 자세한 선택 이유와 한계: [Day 13](docs/day13.md)

## 실행하기

### 1. 준비

JDK 17, MySQL 8, 프로젝트 폴더가 필요합니다. Gradle은 별도 설치 없이 Wrapper를 사용합니다. 아래 터미널 명령은 **프로젝트 루트**에서 실행합니다.

### 2. DB — 처음 설치할 때만

Workbench에서 새 DB를 만들고 기본 스키마로 선택합니다.

```sql
CREATE DATABASE roomflow CHARACTER SET utf8mb4;
USE roomflow;
```

이후 [SQL/schema.sql](SQL/schema.sql), [SQL/insert_initial_data.sql](SQL/insert_initial_data.sql) 순서로 실행합니다. 초기 방 입력은 재실행해도 13실이 중복 생성되지 않습니다.

**이미 Day 12까지 사용한 DB라면 테이블을 삭제하거나 schema.sql을 다시 실행하지 마세요.** 이번 기능은 기존 다섯 테이블을 사용합니다. 새 SQL은 신규 설치용이며 기존 데이터 마이그레이션 도구가 아닙니다.

### 3. 로컬 DB 설정

[예시 설정](src/main/resources/application-local.example.properties)을 참고해 `src/main/resources/application-local.properties`를 만듭니다.

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/roomflow?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=YOUR_DB_USER
spring.datasource.password=YOUR_DB_PASSWORD
```

개발 DB에 접근 가능한 본인 계정을 사용하세요. 일반 서버 계정에는 대상 DB에 필요한 권한만 주고, root 계정을 배포용으로 사용하지 않습니다.

로컬 설정은 Git에서 제외되며 **JAR에도 포함되지 않습니다.** 프로젝트 루트의 `application-local.properties`도 외부 설정으로 읽을 수 있습니다. 둘 다 있으면 루트 파일이 우선합니다.

### 4. JWT 키 설정 후 서버 실행

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
$env:ROOMFLOW_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
.\gradlew.bat bootRun
```

키는 현재 터미널에만 남습니다. 같은 터미널에서는 다음 실행부터 `bootRun`만 하면 됩니다. 키를 바꾸면 기존 토큰은 무효이므로 다시 로그인하세요. 실제 키·DB 비밀번호를 커밋하지 않습니다.

### 5. 브라우저에서 API 확인

- 상태 확인: [health](http://localhost:8080/health)
- API 실행 화면: [Swagger UI](http://localhost:8080/swagger-ui/index.html)
- 명세: [OpenAPI JSON](http://localhost:8080/v3/api-docs)

Swagger에서 회원가입 → 로그인 → 응답의 `accessToken`을 **Authorize**에 입력 → 보호 API를 실행합니다. 입력란에는 토큰만 넣습니다. 기존 DB의 임시 문자열 비밀번호 계정 대신 새 이메일로 가입하세요.

예약 예시는 **내일 이후 정시**로 바꿔 실행합니다. 과거 날짜 예시를 그대로 보내면 400입니다.

## 테스트와 빌드

```powershell
.\gradlew.bat clean build
```

기본 테스트는 독립 H2 DB와 테스트 전용 JWT 키를 사용합니다. MySQL 서버나 실제 키 없이 실행되며 기존 개발 DB를 변경하지 않습니다. 첫 실행은 의존성 다운로드를 위해 인터넷이 필요합니다.

실제 MySQL 동시성·설치 SQL 검증:

```powershell
.\gradlew.bat mysqlTest
```

이 명령은 로컬 설정의 DB 접속 정보를 사용하되 **roomflow_test_무작위값** DB를 새로 생성해 검증하고 삭제합니다. 해당 계정에 테스트 DB 생성·삭제 권한이 필요합니다. 별도 자격 정보는 `ROOMFLOW_TEST_MYSQL_URL`, `ROOMFLOW_TEST_MYSQL_USER`, `ROOMFLOW_TEST_MYSQL_PASSWORD` 환경 변수로 전달할 수 있습니다. 테스트가 강제 종료되면 임시 DB가 남을 수 있으므로 이름을 확인하세요. 기존 roomflow DB에는 쿼리하지 않습니다.

- 기본 결과: `build/reports/tests/test/index.html`
- MySQL 결과: `build/reports/tests/mysqlTest/index.html`
- 실행 JAR: `build/libs/roomflow-0.0.1-SNAPSHOT.jar`
- JAR 실행: `java -Duser.timezone=Asia/Seoul -jar build/libs/roomflow-0.0.1-SNAPSHOT.jar`
- JAR 실행 위치에 외부 DB 설정을 두고 JWT 환경 변수도 설정해야 합니다.

[최종 검증·시연 순서](docs/day14.md) · [트러블슈팅](docs/troubleshooting.md)

## 개발 계획

### 1주 차 — 프로젝트 기반 및 예약 기능 구현

| 일차 | 목표 | 세부 내용 |
|---|---|---|
| Day 1 | 요구사항 및 설계 | 프로젝트 문제 정의, 예약 정책 확정, ERD·API 초안 작성 |
| Day 2 | MySQL 학습 및 DB 설계 | 테이블 생성, PK/FK, JOIN, 인덱스, 트랜잭션 학습 |
| Day 3 | Spring Boot 환경 구성 | Spring Boot 프로젝트 생성, MySQL 연동, 기본 구조 설정 |
| Day 4 | 도메인 구현 | User, Room, Reservation, WaitingQueue 엔티티 및 연관관계 구현 |
| Day 5 | 미팅룸 조회 | 1~13실 목록 및 날짜별 예약 가능 시간 조회 API 구현 |
| Day 6 | 예약·취소 기능 | 예약 생성, 시간 중복 검증, 예약 취소 및 상태 변경 구현 |
| Day 7 | 중간 점검 | 예외 처리, 단위 테스트, Swagger API 문서화, 리팩터링 |

### 2주 차 — 대기열 및 자동 승급 기능 구현

| 일차 | 목표 | 세부 내용 |
|---|---|---|
| Day 8 | 대기열 등록 | 예약 불가 시 대기열 등록 및 내 대기 순번 조회 구현 |
| Day 9 | 취소 시 자동 승급 | 예약 취소 시 첫 대기자에게 예약 제안 생성 |
| Day 10 | 예약 제안 처리 | 제안 수락, 거절, 만료 및 다음 대기자 자동 승급 구현 |
| Day 11 | 체크인·노쇼 처리 | 체크인 API 및 Scheduler를 이용한 노쇼 자동 처리 |
| Day 12 | 인증·인가 | Spring Security와 JWT를 이용한 로그인 및 접근 제어 구현 |
| Day 13 | 동시성 제어 | 동일 시간대 동시 예약 요청 시 단일 예약만 생성되도록 처리 |
| Day 14 | 최종 정리 | 통합 테스트, README 보완, ERD·API 명세·트러블슈팅 문서화 |

## 문서

- [API 명세](docs/api.md)
- [ERD](docs/erd.md)
- [Day 10: 제안 만료](docs/day10.md)
- [Day 11: 체크인·노쇼](docs/day11.md)
- [Day 12: JWT 인증·인가](docs/day12.md)
- [Day 13: 동시성 제어](docs/day13.md)
- [Day 14: 최종 검증](docs/day14.md)

Day 10~12 문서는 당시 개발 기록입니다. 최신 정책과 API는 이 README와 API 명세를 기준으로 합니다.

## 범위 밖과 후속 개선

현재 결과물은 **로컬 실행 가능한 백엔드 MVP**입니다. 실제 학교 앱 연동, 프런트엔드, 문자·푸시 알림, 팀원 관리, 결제·노쇼 패널티는 포함하지 않습니다.

공개 서비스로 운영하기 전에는 HTTPS, 로그인 시도 제한, 비밀번호 재설정·토큰 폐기/갱신, 운영 DB 마이그레이션, 모니터링·백업, 부하 테스트가 필요합니다. 조회 API에는 아직 페이지네이션이 없습니다. 방 단위 잠금은 구현이 단순하지만 서로 다른 시간대 요청도 같은 방이면 순서대로 처리합니다.

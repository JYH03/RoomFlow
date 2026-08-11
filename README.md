# RoomFlow

## 문제정의
학교 중앙 도서관 앱의 그룹 스터디룸의 예약기능을 이용하며 시험기간 빠른 예약 마감 및 노쇼 또는 예약 취소 상황에서 예약을 못한 사람에게 연결되는 시스템을 만들고자 한다. 실제 앱의 기능을 개선한다 가정하며 예약 기능 및 대기열 기능을 추가하여 사용자에게 기회를 제공하도록 한다.

## MVP
실제 도서관 운영 정책에 맞게 다음과 같이 반영
- 미팅룸은 1실 ~ 13실, 1시간 또는 2시간에 한 팀만 예약 가능
- 예약자는 대표자 1명으로 관리
- 같은 사용자는 같은 시간대에 하나의 방만 예약 가능
- 이미 예약된 방에는 대기열 등록 가능
- 대기 제안 유효시간은 10분
- 제안 수락 시 예약 확정, 거절 또는 만료 시 다음 대기자에게 제안
- 예약 시작 후 5분까지 체크인 하지 않으면 노쇼
- 노쇼, 예약 취소의 경우 다음 대기자에게 예약 제안

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

## 최종 구현 목표

- 1실부터 13실까지의 미팅룸 예약
- 시간대별 단일 팀 예약
- 예약 취소 및 체크인 기능
- 예약 마감 시 대기열 등록
- 취소 또는 노쇼 발생 시 대기자 자동 승급
- 예약 제안 수락 및 만료 처리
- JWT 기반 인증·인가
- 동시 예약 상황에 대한 트랜잭션 처리

## 사용 스택 및 기술
## Tech Stack

| 분류 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot |
| Web | Spring Web, Spring Validation |
| Data Access | Spring Data JPA, Hibernate |
| Database | MySQL 8 |
| Security | Spring Security, JWT |
| Build Tool | Gradle |
| API Documentation | Swagger / Springdoc OpenAPI |
| Testing | JUnit 5, Mockito |
| Scheduling | Spring Scheduler |
| Version Control | Git, GitHub |
| Development Environment | Visual Studio Code, MySQL Workbench |

## Architecture

- Layered Architecture
  - Controller: HTTP 요청·응답 처리
  - Service: 예약, 대기열, 노쇼 등 비즈니스 로직 처리
  - Repository: 데이터베이스 접근
  - Entity: 도메인 및 테이블 매핑

## Key Technologies

- **Spring Data JPA**: 미팅룸, 예약, 대기열 데이터를 객체 중심으로 관리
- **MySQL**: 예약·대기열·예약 제안 데이터를 영속적으로 저장
- **Spring Security + JWT**: 사용자 인증 및 본인 예약에 대한 접근 제어
- **Spring Scheduler**: 예약 제안 만료와 노쇼를 주기적으로 확인하고 처리
- **JUnit 5 / Mockito**: 예약 생성, 대기열 승급 등 핵심 비즈니스 로직 테스트
- **Swagger**: REST API 명세를 문서화하고 테스트
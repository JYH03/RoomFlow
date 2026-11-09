# 트러블슈팅

## Java package와 폴더 불일치

src/main/java 아래 경로와 package를 맞춥니다. `com/jyh03/roomflow`의 선언은 `package com.jyh03.roomflow;`입니다. 역슬래시를 넣지 않습니다. src/test/java에도 같은 규칙을 적용합니다. Java는 대소문자를 구분하므로 @ID가 아니라 @Id입니다.

## cannot find symbol

파일명, public 클래스/enum명, package, import를 함께 확인합니다. WaitingQueueStatus나 DTO를 참조하는 코드만 작성하고 실제 파일을 만들지 않으면 컴파일되지 않습니다.

## MySQL 1046: No database selected

Workbench 왼쪽 SCHEMAS에서 대상 DB를 더블클릭하거나 `USE roomflow;`를 먼저 실행합니다.

## MySQL 1064: SQL 문법 오류

CONSTRAINT나 INDEX는 CREATE TABLE 내부 또는 ALTER TABLE 절로 사용해야 합니다. 단독 문장으로 실행하지 않습니다. 신규 설치는 수정된 SQL/schema.sql을 사용합니다. 기존 테이블에 재실행하지 않습니다.

## 취소 후 같은 시간 재예약이 DB UNIQUE 때문에 실패

취소 이력을 남기는 모델에서는 예약 테이블의 단순 UNIQUE(room_id,start_at), UNIQUE(user_id,start_at)가 이력까지 차단합니다. 현재 설계는 일반 검색 인덱스와 서비스 트랜잭션·잠금을 사용합니다.

기존 인덱스를 제거할 때 FK가 사용 중이면 1553 오류가 납니다. 외래 키 선두 열을 포함하는 대체 일반 인덱스를 먼저 만들고 제약을 확인해야 합니다. 무조건 FK나 테이블을 삭제하지 마세요. 현재 스키마는 필요한 인덱스를 포함합니다.

## 401 / 403

401은 로그인 또는 토큰 재발급이 필요합니다. 브라우저 주소창에는 Bearer 헤더가 없으므로 보호 API는 Swagger Authorize나 PowerShell 헤더를 사용합니다. 403은 토큰은 있지만 대상의 소유자가 아닌 경우입니다. userId를 본문에 추가해도 소유자는 바뀌지 않습니다.

## JWT 키 설정 오류로 시작 실패

ROOMFLOW_JWT_SECRET에 최소 32바이트 무작위 값의 Base64 인코딩을 설정합니다. README의 생성 명령을 서버를 실행할 터미널에서 먼저 실행하세요. 실제 키는 코드·Git·화면 캡처에 남기지 않습니다.

## DB에 저장한 테스트 사용자가 로그인되지 않음

예전 SQL 예제의 password_hash가 임시 문자열이면 BCrypt 검증을 통과하지 못합니다. 기존 데이터를 임의로 변경하지 말고 새 이메일로 회원가입하세요.

## 400: 시간이 잘못됨

최종 MVP는 미래 날짜의 정시 시작·정확히 1시간·09~22시만 신규 예약합니다. 과거 날짜, 14:30 시작, 90분 예약은 거절합니다. Day 10/11의 과거 수동 테스트는 당시 기록이며 현재는 자동 테스트로 시간 경계와 노쇼를 재현합니다.

## 409: 예약 또는 제안 충돌

이미 예약됨, 중복 대기, 제안 만료/중복 수락 등의 정상적인 충돌 응답입니다. 내 예약과 제안을 다시 조회하세요. 잠금 충돌 안내가 나올 때만 잠시 후 재시도합니다.

## 제안이 만료되었는데 슬롯이 아직 unavailable

스케줄러가 만료 처리와 다음 대기자 승급을 커밋할 때까지 PENDING을 보호하는 의도된 동작입니다. 기본 주기는 30초이며 대량 처리나 장애 시 늦어질 수 있습니다. 스케줄러 enabled 설정과 서버 로그를 확인하세요.

## PowerShell에서 요청이 잘 안 됨

서버가 실행 중인 터미널은 그대로 두고 다른 터미널을 엽니다. 코드 블록의 내용만 복사하며 PS 경로, > 표시, 역따옴표 펜스는 복사하지 않습니다. 여러 줄 백틱 연결보다 한 줄 Invoke-RestMethod 명령이 단순합니다. 미래 시각 생성은 Day 12 문서를 참고하세요.

## mysqlTest 실패

기본 build는 MySQL 없이 통과합니다. mysqlTest만 실패한다면 MySQL 실행 여부와 테스트용 CREATE/DROP DATABASE 권한을 확인합니다. 일반 앱 계정에 넓은 권한을 추가하기보다 ROOMFLOW_TEST_MYSQL_* 환경 변수로 별도 테스트 계정을 사용하세요.

테스트는 roomflow_test_ 뒤에 32자리 무작위 문자열이 붙은 DB만 생성·삭제합니다. 실행을 강제 종료했다면 해당 이름과 내용을 확인한 후 남은 임시 DB만 정리합니다. roomflow DB를 지우지 마세요.

# Day 12: 회원가입, 로그인, JWT 인증·인가

> 당시 개발 기록입니다. Day 14 현재 정책·API는 [최신 API 명세](api.md)와 [최종 안내](day14.md)를 기준으로 합니다. 과거 날짜나 정시가 아닌 신규 예약 예시는 현재 검증 정책에 맞게 변경해야 합니다.

## 변경 내용

- 회원가입 `POST /api/auth/signup`: 이메일 중복 검사, BCrypt 비밀번호 해시 저장.
- 로그인 `POST /api/auth/login`: 검증 성공 시 1시간짜리 JWT 발급.
- 내 정보 `GET /api/auth/me`: 토큰의 사용자 정보만 반환.
- 예약·대기열 생성과 조회는 JWT의 사용자 ID 기준. 요청 DTO에서 `userId` 제거.
- 예약 취소·체크인, 대기열 취소, 제안 수락은 서비스에서 소유권 검사. 타인이면 403.
- JWT 없는 요청과 위조·만료 토큰은 401 JSON 응답. 회원가입 중복 이메일은 409.
- 로그인·회원가입, 미팅룸 조회, health는 공개. 세션·쿠키 로그인 없이 Bearer 헤더 사용.
- DB 테이블 변경 없음. 기존 테스트 계정과 예약 기록은 수정하지 않음.

## 1. 서버 실행 터미널: JWT 키 설정

서버를 중지한 후 프로젝트 루트에서 실행합니다. 무작위 키는 현재 PowerShell 프로세스의 환경 변수에만 넣고 출력하지 않습니다.

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
$env:ROOMFLOW_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
.\gradlew.bat bootRun
```

같은 터미널에서 재시작할 때는 `bootRun`만 실행하면 됩니다. 새 터미널에서 다른 키를 생성하면 기존 토큰은 무효이므로 다시 로그인합니다.
같은 키를 유지하려면 환경 변수로 관리하거나, 이미 Git에서 제외한 `application-local.properties`에 `roomflow.jwt.secret=<생성한 Base64 키>`를 직접 설정할 수 있습니다. 실제 키는 공유하거나 커밋하지 않습니다.

## 2. 새 터미널: 회원가입·로그인

이전의 사용자 ID 1·2·3은 임시 비밀번호 값으로 만들었으므로, 임의로 비밀번호를 바꾸지 않습니다. 새 이메일로 아래 테스트를 진행합니다.

```powershell
$base = 'http://localhost:8080'
$tag = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$emailA = "day12-a-$tag@example.com"
$emailB = "day12-b-$tag@example.com"
$password = 'LocalTestPass123!'

$body = @{ email = $emailA; password = $password; name = 'Tester A' } | ConvertTo-Json -Compress
$userA = Invoke-RestMethod -Uri "$base/api/auth/signup" -Method Post -ContentType 'application/json' -Body $body -ErrorAction Stop
$userA

$body = @{ email = $emailA; password = $password } | ConvertTo-Json -Compress
$loginA = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body $body -ErrorAction Stop
$headersA = @{ Authorization = "Bearer $($loginA.accessToken)" }
Invoke-RestMethod -Uri "$base/api/auth/me" -Headers $headersA -ErrorAction Stop
```

`id`, `email`, `name`이 나오면 성공. 토큰이나 비밀번호를 캡처해 공유하지 않습니다. 토큰 발급 후 1시간이 지나면 로그인 요청을 다시 실행해 헤더를 갱신하세요.

## 3. 로그인한 사용자로 예약

내일 오후 2시, 1번 방을 사용합니다. 이미 예약된 슬롯이면 비어 있는 방·시간으로 변경하세요.

```powershell
$start = (Get-Date).Date.AddDays(1).AddHours(14)
$body = @{
    roomId = 1
    startAt = $start.ToString('yyyy-MM-ddTHH:mm:ss')
    endAt = $start.AddHours(1).ToString('yyyy-MM-ddTHH:mm:ss')
} | ConvertTo-Json -Compress

$reservation = Invoke-RestMethod -Uri "$base/api/reservations" -Method Post -Headers $headersA -ContentType 'application/json' -Body $body -ErrorAction Stop
$reservation
```

요청에 `userId`가 없어도 응답의 `userId`는 `$userA.id`가 됩니다. 다른 ID를 본문에 넣어도 소유자는 인증 토큰으로 정해집니다.

## 4. 인증 누락·타인 변경 차단 확인

헤더 없이 조회: 401이 정상입니다.

```powershell
try {
    Invoke-RestMethod -Uri "$base/api/waiting-queues" -ErrorAction Stop
} catch {
    [int]$_.Exception.Response.StatusCode
}
```

두 번째 계정을 만들고 로그인합니다.

```powershell
$body = @{ email = $emailB; password = $password; name = 'Tester B' } | ConvertTo-Json -Compress
$userB = Invoke-RestMethod -Uri "$base/api/auth/signup" -Method Post -ContentType 'application/json' -Body $body -ErrorAction Stop
$body = @{ email = $emailB; password = $password } | ConvertTo-Json -Compress
$loginB = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body $body -ErrorAction Stop
$headersB = @{ Authorization = "Bearer $($loginB.accessToken)" }

try {
    Invoke-RestMethod -Uri "$base/api/reservations/$($reservation.id)/cancel" -Method Post -Headers $headersB -ErrorAction Stop
} catch {
    [int]$_.Exception.Response.StatusCode
}
```

403이면 B가 A의 예약을 취소하지 못한 것입니다. A의 토큰으로 요청하면 취소됩니다.

```powershell
Invoke-RestMethod -Uri "$base/api/reservations/$($reservation.id)/cancel" -Method Post -Headers $headersA -ErrorAction Stop
```

## 이전 API 요청을 바꾸는 방법

- 예약·대기열 등록 본문에서 `userId` 제거.
- 각 요청에 해당 계정의 `-Headers $headersA` 또는 `-Headers $headersB` 추가.
- 내 대기열 조회는 `/api/waiting-queues`만 사용. `?userId=...`는 필요 없고 조회 대상 변경 효과도 없음.
- 취소, 체크인, 제안 수락은 해당 자원의 소유자 토큰으로 호출.
- 브라우저 주소창만으로 보호 API를 열면 Bearer 헤더가 없어 401. PowerShell 등 헤더를 보낼 수 있는 도구 사용.

## 검증과 범위

```powershell
.\gradlew.bat build
```

자동 테스트는 테스트 전용 키와 비활성화한 스케줄러를 사용합니다. JWT 서명·만료·발급자·대상 검증, 암호화와 로그인, 소유권 차단, 요청 ID 위조, 공개/비공개 API 접근을 검증합니다. HTTP 보안 테스트는 실제 보안 필터와 서비스를 통과하며 Repository만 대체하므로 로컬 DB의 계정을 만들거나 변경하지 않습니다.

이번 범위는 Access Token 인증입니다. Refresh Token, 비밀번호 재설정, 서버 측 토큰 폐기, 관리자 권한, 로그인 시도 제한은 미구현입니다. 외부 배포 전에는 HTTPS와 로그인 시도 제한 등이 추가로 필요합니다. 동시 예약·노쇼·수락의 잠금 및 제안 시간대 선점 방지는 Day 13 과제입니다.

구현 참고: [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Spring Boot 의존성 목록](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html).

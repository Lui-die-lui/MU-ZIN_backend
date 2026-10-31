# MUZIN Backend
클래식 악기 레슨 예약 플랫폼 MUZIN의 API 서버

- 회원·아티스트 인증, 레슨·타임슬롯, 예약, 채팅, 알림, 리뷰, 관리자 기능을 제공합니다.
- 서비스 기획과 화면은 [프론트엔드 저장소](https://github.com/Lui-die-lui/MU-ZIN_frontend)를 참고해 주세요.

**서비스 주소: https://mu-zin.com/** · **API: https://api.mu-zin.com/**

---

## 1. 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어·프레임워크 | Java 17, Spring Boot 4.0 |
| 데이터 | PostgreSQL, Spring Data JPA, QueryDSL, Flyway |
| 인증 | Spring Security, JWT(jjwt), OAuth2 Client (Google·Naver·Kakao) |
| 실시간 | WebSocket (STOMP), Web Push (VAPID) |
| 외부 연동 | Gmail SMTP, OpenAI API (AI 레슨 내비게이터) |
| 인프라 | AWS EC2, nginx, Docker(PostgreSQL), GitHub Actions |

---

## 2. 주요 기능

| 기능 | 내용 |
|---|---|
| 회원 | 이메일·소셜 가입, 이메일 인증, 약관 동의 기록, 회원 탈퇴 |
| 아티스트 | 인증 신청과 관리자 심사, 프로필 관리, 검색, 팔로우 |
| 레슨 | 레슨 등록·수정, 단건·반복 타임슬롯 생성, 추천 |
| 예약 | 요청·확정·거절·취소·완료·만료 상태 관리 |
| 소통 | 실시간 채팅, 레슨 문의, 리뷰와 답글 |
| 알림 | 앱 내 실시간 알림, 웹 푸시 |
| 운영 | 관리자 대시보드, 사용자 관리, 신고 처리, 이상 행동 감지 |
| AI 레슨 내비게이터 | 서비스 DB를 도구로 조회해 레슨을 안내하는 대화형 도우미 |

---

## 3. 패키지 구조

```
com.muzin.mu.zin
├── ai/          AI 레슨 내비게이터 (엔진, 제공자, 도구, 대화 기록)
├── common/      공통 값 (KST 시간 등)
├── config/      Security, WebSocket, JPA, QueryDSL 설정
├── controller/  REST·STOMP 컨트롤러 (admin/ 포함)
├── dto/         요청·응답 DTO
├── entity/      JPA 엔티티와 enum
├── event/       도메인 이벤트
├── exception/   전역 예외 처리
├── policy/      타임슬롯 정책
├── repository/  Spring Data 저장소, QueryDSL 검색
├── schduler/    예약 상태 자동 전환
├── security/    JWT 필터, OAuth2 처리, 요청 제한, WebSocket 인증
└── service/     도메인별 비즈니스 로직
```

공통 응답 형식은 `ApiRespDto`입니다.

---

## 4. 설계 포인트

### 예약 상태

```
REQUESTED ──수락──▶ CONFIRMED ──레슨 종료──▶ COMPLETION_PENDING ──▶ COMPLETED
    │                   │                     아티스트 확인 또는
    ├─▶ REJECTED        └─▶ CANCELED          종료 1시간 뒤 자동 완료
    ├─▶ CANCELED
    └─▶ EXPIRED (응답 없이 시작 시간 경과)
```

- 상태 변경은 엔티티의 도메인 메서드(`confirm()`, `cancel()` 등)에서만 일어나고, 허용되지 않은 변경은 막습니다.
- 같은 타임슬롯의 동시 예약은 비관적 락(`findByIdForUpdate`)으로 막습니다.
- 수락과 취소처럼 같은 예약을 동시에 바꾸는 경우는 낙관적 락(`@Version`)으로 한쪽을 실패시킵니다.
- 예약 당시 가격(`price_at_booking`)과 단계별 시각을 예약에 함께 저장합니다.

### 이벤트 기반 알림

알림, 웹 푸시, 채팅의 실시간 전송은 이벤트 리스너로 분리해 예약·채팅 로직과 나눴습니다.

### 회원 탈퇴

- 진행 중인 예약이 있으면 탈퇴할 수 없습니다.
- 회원 정보는 알아볼 수 없게 바꾸고, 상대와 함께 쓰는 기록에는 "탈퇴한 회원"으로 표시합니다.
- 이용 정지 이력이 있는 계정만 이메일을 HMAC 해시로 남겨 일정 기간 재가입을 제한합니다.

### AI 비용 관리

- `AI_PROVIDER`의 기본값은 `stub`이라, 설정하지 않으면 실제 호출과 과금이 없습니다.
- 주간 대화 수, 대화당 메시지 수, 월간 비용에 상한이 있습니다.

### 예약 작업

| 주기 | 작업 |
|---|---|
| 1분 | 종료된 레슨을 완료 대기로 전환, 자동 완료, 응답 없는 요청 만료, 멈춘 AI 질문 복구 |
| 10분 | 이상 행동 감지, 요청 제한 기록 정리 |
| 매일 04:10 | 기간이 지난 재가입 제한 삭제 |
| 매일 04:20 | 보관 기간이 지난 AI 대화 삭제 |
| 매일 04:40 | 30일이 지난 로그인 실패 기록 삭제 |

---

## 5. 로컬 실행

PostgreSQL이 필요합니다. 기본 주소는 `localhost:3311/muzin_db`입니다.

```bash
cp .env.example .env    # 값을 채워 주세요
./gradlew bootRun       # http://localhost:8080
./gradlew test          # .env 와 DB 필요
./gradlew bootJar       # 배포용 jar (build/libs/)
```

Windows에서는 `gradlew.bat`을 사용합니다. `.env`는 프로젝트 루트에서 자동으로 읽습니다.

> `/` 접속 시 401이 나오면 서버가 정상 동작 중인 것입니다.

**환경변수** (`.env`, git에 올리지 않음. 전체 목록은 [`.env.example`](.env.example))

| 이름 | 용도 | 필수 |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | DB 접속 정보 | `DB_URL` 외 필수 |
| `JWT_SECRET` | JWT 서명 키 | 필수 |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail 앱 비밀번호 | 필수 |
| `GOOGLE_*`, `NAVER_*`, `KAKAO_*` | 소셜 로그인 | 필수 |
| `WITHDRAW_EMAIL_HMAC_SECRET` | 재가입 제한용 이메일 해시 키 | 운영 필수 |
| `REJOIN_BLOCK_MONTHS` | 재가입 제한 기간(개월) | 선택 |
| `SIGNUP_CONSENT_REQUIRED` | 동의 값 없는 가입 요청 거절 여부 | 선택 |
| `FRONTEND_URL`, `BACKEND_URL` | 서비스 주소 | 선택 |
| `SHOW_SAMPLE_DATA` | 샘플 계정 레슨 표시 (로컬 전용) | 선택 |
| `AI_*`, `OPENAI_API` | AI 레슨 내비게이터 | 선택 |
| `WEBPUSH_*` | 웹 푸시. 없으면 푸시만 꺼짐 | 선택 |

---

## 6. DB 마이그레이션

- 스키마는 Flyway가 관리하고, 서버가 시작될 때 자동으로 적용됩니다.
- 파일은 [`src/main/resources/db/migration`](src/main/resources/db/migration)에 있습니다.
- `ddl-auto=validate`라서 엔티티와 테이블이 맞지 않으면 서버가 시작되지 않습니다.
- 이미 적용한 파일은 고치지 않고, 새 버전 파일을 추가합니다.

---

## 7. 배포

`main`에 푸시되면 [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml)이 자동으로 배포합니다. Actions 탭에서 직접 실행할 수도 있습니다.

```
main 푸시
  1. jar 빌드 (테스트 제외)
  2. AWS 로그인 (OIDC)
  3. 보안 그룹에 "이번 Actions 서버 IP만" SSH 허용 추가
  4. 서버에 jar 업로드, 이전 jar 백업, 서비스 재시작
  5. 응답 확인. 실패하면 이전 jar로 되돌림
  6. SSH 허용 규칙과 키 삭제 (성공·실패와 관계없이 항상)
```

> 배포 빌드는 테스트를 건너뛰므로, 머지 전에 로컬에서 `./gradlew test`를 실행해 주세요.

```
https://api.mu-zin.com → nginx → Spring Boot (서버 내부 8080)
                                  ├─ PostgreSQL (Docker, 서버 내부에서만 접속)
                                  └─ /ws (WebSocket)
```

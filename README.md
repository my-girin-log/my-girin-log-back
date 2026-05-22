# my-grin-log-back

우테고치(내가그린기린기록) 백엔드. **하루치 채팅 → 다이어리 → 회고 글** 흐름과 펫 다마고치 상태 머신을 다룬다.

- Java 21 / Spring Boot 3.3 / Spring Data JPA / Spring AI (Anthropic Claude)
- H2 파일 모드(dev) / MySQL 8 (prod) 듀얼 프로필
- 프론트 레포: [my-girin-log-front](https://github.com/my-girin-log/my-girin-log-front) — 본 백엔드의 `/api/v1` 을 그대로 호출
- 카키 레퍼런스: [haeyoon1/techgochi](https://github.com/haeyoon1/techgochi) (Node.js/TS) — 모델 분배, 3-pass 회고, `<output>` 강제 패턴을 Spring 으로 포팅

## 빠른 시작 (프론트와 함께)

```bash
# 터미널 1
cd ~/my-girin-log-back
unset SPRING_PROFILES_ACTIVE DB_URL DB_USER DB_PASSWORD   # 이전 prod 변수 청소
./gradlew bootRun

# 터미널 2
cd ~/my-girin-log-front
npm install   # 첫 1회
npm run dev
```

브라우저에서 **http://127.0.0.1:5173** 열기. 첫 진입 시 `/api/v1/auth/demo` 자동 호출 → 토큰 발급 → localStorage 저장 → 펫(1-calf-good) + 4프레임 sprite 애니메이션.

> dev 프로필 = H2 file mode (`./build/h2/mygrinlog.mv.db`). H2 콘솔: `http://localhost:8080/h2-console`
> (JDBC URL `jdbc:h2:file:./build/h2/mygrinlog;MODE=MySQL;DATABASE_TO_LOWER=TRUE`, user `sa`, no password)

## Claude API 켜기

LLM 없이도 stub 응답으로 모든 엔드포인트가 동작하지만 실제 텍스트를 보고 싶으면:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export LLM_ENABLED=true
./gradlew bootRun
```

라이브 호출 검증 (비용 발생):
```bash
export LLM_LIVE_TEST=1
./gradlew test --tests com.mygrinlog.llm.LlmLiveSmokeTest -i
```

## API 명세 (`/api/v1`)

**인증**: `Authorization: Bearer <token>` 필수. 데모용으로 `X-Demo-User-Id: <id>` 헤더 허용 (`wootegotchi.auth.allowDemoHeader=true` 일 때).

| 영역 | 메서드 | 경로 | 비고 |
| --- | --- | --- | --- |
| Auth | GET | `/auth/github` | GitHub OAuth 시작 (302). 키 미설정이면 callback 으로 즉시 redirect |
| | GET | `/auth/github/callback` | 콜백 — 데모 유저 생성/조회 + 토큰 발급 |
| | POST | `/auth/demo` | `{githubId, nickname?, avatarUrl?}` → 즉시 토큰 (프론트가 첫 진입 시 자동 호출) |
| User | GET | `/users/me?level=&condition=` | user + pet. 쿼리로 펫 응답만 강제 변경 (DB 미변경, 시연/QA 용) |
| | POST | `/users/onboarding` | `{sources[], rawText, nickname?}` → Persona 생성/갱신 |
| Chat | GET | `/chats/active` | 오늘(06:00 KST 경계) 활성 세션 + 메시지. 없으면 lazy 생성 |
| | POST | `/chats/message` | `{sessionId?, content, source?}` → user/assistant 메시지 + EXP+2 |
| Diary | POST | `/diaries/rollup` | 수동 롤업 트리거 (스케줄러와 같은 메서드 공유) |
| | GET | `/diaries?yearMonth=YYYY-MM` | yearMonth 옵셔널. 없으면 전체. 잔디용 경량 응답 |
| | GET / PUT / DELETE | `/diaries/{dateKey}` | 상세 (없으면 404) / 수정 / 삭제 |
| Review | POST | `/retrospectives` | 기간/옵션 → 3-pass LLM → 저장 + EXP+10 |
| | GET | `/retrospectives` | 내 회고 목록 (최신순) |
| | GET / PUT / DELETE | `/retrospectives/{id}` | 상세 / 수정 / 삭제 |

### 펫 응답 형식 (워넬 자료 §3 그대로)

```json
{
  "level": 1,
  "stage": "adolescent",
  "condition": "bad",
  "exp": 45,
  "expIntoLevel": 5,
  "levelUpExp": 10,
  "lastActivityAt": "2026-05-21T15:30:00Z",
  "meta": {
    "stateNumber": 5,
    "stateKey": "5-adolescent-bad",
    "totalFrames": 4,
    "spriteRowIndex": 4
  }
}
```

[`PetStageCatalog`](src/main/java/com/mygrinlog/pet/PetStageCatalog.java) 가 level×condition → stage / stateNumber / stateKey / spriteRowIndex 9-state 매핑을 보유.

> 백엔드 `level` 은 `0|1|2` (스펙), 프론트 `level` 은 `1|2|3` (디자인 합의) — 프론트의 `src/api/realApi.ts` 가 +1 시프트로 흡수.

## 데모 시나리오 (백엔드만, curl)

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/demo \
  -H 'Content-Type: application/json' \
  -d '{"githubId":"woowa_giraffe","nickname":"우테코기린"}' | jq -r .token)

# 펫 강제 변경 (DB 안 건드림)
curl -s "localhost:8080/api/v1/users/me?level=2&condition=terrible" \
  -H "Authorization: Bearer $TOKEN" | jq .pet

# 메시지 전송 → AI 역질문 받기
curl -s -X POST localhost:8080/api/v1/chats/message \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"content":"오늘 로또 미션에서 예외 처리 위치 한참 헤맸음"}' | jq

# 수동 롤업
curl -s -X POST localhost:8080/api/v1/diaries/rollup \
  -H "Authorization: Bearer $TOKEN" | jq

# 회고 생성
curl -s -X POST localhost:8080/api/v1/retrospectives \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"tech_blog","rangeStartDate":"2026-05-15","rangeEndDate":"2026-05-21","promptOptions":{"focus":"트러블슈팅"}}' | jq
```

## 핵심 설계 (스펙 §0–§5 준수)

1. **시각은 UTC, dateKey 는 06:00 KST −6h 파생** — [`ServiceClock`](src/main/java/com/mygrinlog/common/time/ServiceClock.java)
2. **펫 condition 은 저장값이 아님** — `last_activity_at + now` 기반 lazy 재계산. `condition_cache` 컬럼은 "마지막으로 본 값" 캐시
3. **LLM 호출은 항상 트랜잭션 밖** — `RollupService` / `ChatService` / `RetrospectiveService` 모두 read TX → LLM(밖) → write TX 로 `TransactionTemplate` 분리. `@Transactional` self-call 함정 회피
4. **견고함은 둘만** — 유저별/메시지별 try/catch + LLM 트랜잭션 분리. LLM 자체 재시도 3회. 백오프·데드레터·락은 의도적으로 안 함
5. **회고의 `sourceDiaryIds` 는 백엔드 주입** — AI 가 출력해도 무시. FK 아님(스냅샷) — 다이어리가 수정/삭제돼도 회고 보존
6. **펫 응답은 stateKey 방식** — 프론트가 로컬 자산 매핑 또는 CSS 스프라이트 변위로 렌더. CDN 의존 0

## LLM 모델 분배 (`application.yml`)

카키 레퍼런스 그대로:

| 생성기 | 모델 | temperature | maxTokens | 근거 |
| --- | --- | --- | --- | --- |
| persona | claude-opus-4-7 | 0.3 | 4000 | 어조 명세 정밀도 |
| question | claude-haiku-4-5-20251001 | 0.7 | 200 | 실시간 한 줄 |
| diary | claude-sonnet-4-6 | 0.2 | 4000 | 정리, 창작 금지 |
| retroAnalysis | claude-sonnet-4-6 | 0.3 | 3000 | 추출 |
| retroWriting | claude-opus-4-7 | 0.6 | 6000 | 본문 집필 |
| critique | claude-sonnet-4-6 | 0.1 | 4000 | 엄격 검토 |

> Opus 4.7 은 temperature 미지원 — `LlmClient` 가 모델명 보고 자동 제외.

### 3-pass Retrospective 파이프라인 (카키 차용)

```
diaries + persona
        ↓ (analysis prompt, Sonnet)
RetrospectiveAnalysis (key_themes, emotional_arc, ...)
        ↓ (writing prompt, Opus)
RetrospectiveDraft (title, markdown, summary, tags)
        ↓ (critique prompt, Sonnet, temp=0.1)
Critique { passes, issues[], revised_markdown? }
        ↓
- passes / minor only            → draft 그대로
- critical/major + revised 있음  → revised 채택
- critical/major + revised 없음  → fix-up prompt 로 1회 재작성
```

## MySQL 운영

```bash
# 1) MySQL 준비
brew install mysql && brew services start mysql
mysql -uroot <<'SQL'
CREATE DATABASE mygrinlog CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'mygrinlog'@'localhost' IDENTIFIED BY 'mygrinlog!';
GRANT ALL ON mygrinlog.* TO 'mygrinlog'@'localhost';
FLUSH PRIVILEGES;
SQL
mysql -umygrinlog -p'mygrinlog!' mygrinlog < src/main/resources/db/schema-mysql.sql

# 2) 서버 실행
export SPRING_PROFILES_ACTIVE=prod
export DB_PASSWORD='mygrinlog!'
./gradlew bootRun
```

스키마: [src/main/resources/db/schema-mysql.sql](src/main/resources/db/schema-mysql.sql). prod 의 `ddl-auto=validate`. 실제 마이그레이션은 Flyway/Liquibase 권장.

## 패키지 구조

```
com.mygrinlog
├── MyGrinLogApplication
├── common
│   ├── time/ServiceClock                              # 06:00 KST 경계
│   ├── jpa/{BaseTimeEntity, Json*Converter}           # MySQL JSON / H2 CLOB 양립
│   ├── llm/{LlmClient, LlmProperties, PromptLoader}   # Claude 통합 + 재시도 + JSON 추출
│   └── web/{ApiPaths, CorsConfig, GlobalExceptionHandler, *Exception}
├── auth/{AuthController, TokenStore, @CurrentUser, CurrentUserArgumentResolver, WebConfig}
├── user/{User, UserController, UserRepository}
├── persona/{Persona, PersonaAnalysis, PersonaDraft, PersonaPacker, PersonaService, PersonaGenerator}
├── chat/{DailyChatSession, ChatMessage, ChatService, ChatController, ReverseQuestionGenerator}
├── diary/{Diary, DiaryRepository, DiaryController, DiaryGenerator, DiaryDraft}
├── retrospective/{Retrospective, RetrospectiveService, RetrospectiveController, RetrospectiveGenerator, RetrospectiveAnalysis, Critique}
├── pet/{PetState, PetStateService, PetView, PetSnapshot, PetStageCatalog, PetImageUrlBuilder, PetProperties}
└── batch/{RollupService, RollupScheduler, RollupController, RollupResult}
```

## 테스트

```bash
./gradlew test           # 27개
./gradlew test --rerun-tasks   # 캐시 무시하고 강제 재실행
```

| 클래스 | 수 | 검증 대상 |
| --- | --- | --- |
| ServiceClockTest | 4 | 스펙 §3.1 06:00 경계 |
| PetStateServiceTest | 7 | Level/Condition 순수 함수 |
| PetImageUrlBuilderTest | 2 | 컨벤션 URL |
| RollupServiceIntegrationTest | 3 | 빈 세션/메시지 세션/미래 세션 |
| ApiE2eTest | 8 | `/api/v1` 전체 흐름 (auth/users/chats/diaries/retrospectives) |
| PersonaMarkdownRoundtripTest | 1 | 한국어 markdown DB 왕복 보존 (in-memory) |
| PersonaMarkdownFileH2RoundtripTest | 1 | file-mode H2 왕복 회귀 (이전 `@Lob` 버그 방지) |
| LlmLiveSmokeTest | 1 (자동 skip) | `LLM_LIVE_TEST=1` + `ANTHROPIC_API_KEY` 있을 때만 |

## 트러블슈팅

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| 프론트가 401 도배 | 백엔드 재시작 후 in-memory 토큰 모두 무효 | 브라우저 콘솔: `localStorage.clear()` 후 새로고침 |
| `Access denied for user 'mygrinlog'@'localhost'` | prod 프로필인데 MySQL 미준비 | `unset SPRING_PROFILES_ACTIVE` 후 다시 |
| `Database is already closed [90121-224]` | JVM 종료 시 in-memory H2 가 먼저 닫힘. 무해 | 무시. 결과엔 영향 없음 |
| `MVStoreException: file is locked` | 다른 프로세스(bootRun)가 같은 H2 파일을 잡고 있음 | 그 프로세스 종료 또는 `rm -rf build/h2/` |
| markdown 일부가 잘려서 응답됨 | (이전 버그) `@Lob` + file-mode H2 + UTF-8 청크 경계 | 이미 수정됨. `@Lob` 제거됨. 회귀 테스트 추가 |
| SQL 로그가 너무 시끄러움 | `show-sql: true` | `application-dev.yml` 에서 `false`, 또는 `SPRING_JPA_SHOW_SQL=false ./gradlew bootRun` |

## 다음 단계 (담당 외)

- **인증 강화**: 현재는 데모용 in-memory Bearer 토큰. 운영 도입 시 `/auth/github/callback` 안에 실제 GitHub access_token 교환 + JWT/Session 도입
- **과거 채팅 세션 조회**: `/chats/active` 는 오늘만 반환. 프론트가 ArchiveScreen 에서 과거 dateKey 의 세션을 보려면 별도 엔드포인트 필요
- **운영 배포**: Dockerfile / CI / 환경별 secrets 관리

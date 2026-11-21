# Backend (Spring Boot)

저장소를 처음 받은 팀원이 백엔드를 로컬에서 실행하기 위한 가이드입니다.

## 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 17 |
| Spring Boot | 4.0.x |
| Gradle | 9.x (Wrapper 포함, 별도 설치 불필요) |
| DB | PostgreSQL 18 (Docker) |
| SQL 매퍼 | MyBatis |
| DB 마이그레이션 | Flyway |

## 0. 사전 준비

- **JDK 17** 설치
  - 확인: `java -version` → `17.x.x`
- **Docker Desktop** 설치 후 실행
- **IntelliJ IDEA** (권장)
  - `File → Settings(Mac: Settings…) → Build Tools → Gradle → Gradle JVM`을 **17**로 설정
  - Lombok 사용: `Settings → Build → Compiler → Annotation Processors → Enable annotation processing` 체크

## 1. 저장소 받기

```bash
git clone <저장소 주소>
cd ai-youth-pjt
```

> **Windows 사용자**: 줄바꿈 문제를 막기 위해 clone 전에 한 번 실행해 두세요.
> ```bash
> git config --global core.autocrlf false
> ```
> (저장소의 `.gitattributes`가 줄바꿈을 관리합니다.)

## 2. `.env` 만들기 (`backend` 폴더)

```bash
cd backend

# Mac / Git Bash
cp .env.example .env

# Windows PowerShell
Copy-Item .env.example .env
```

`.env`에서 비어 있는 값을 채웁니다. **실제 값은 PM에게 문의**하세요.
서버 시작에는 DB 비밀번호가 필요합니다. 주소·경로 연동까지 확인하려면 아래 두 공급원 키도 등록합니다.

```
DB_NAME=ai_youth_pjt_db
DB_USER=postgres
DB_PASSWORD=<비밀번호>
KAKAO_REST_API_KEY=<카카오 로컬 REST API 키>
ODSAY_WEB_KEY=<ODsay 서버 API 키>
```

> - `backend/.env` 하나를 Docker(DB)와 Spring(백엔드)이 **같이** 읽습니다. 값을 바꾸면 양쪽에 모두 반영됩니다.
> - 프론트엔드 키는 `frontend/.env`에 따로 넣습니다.
> - 값에 따옴표(`"`)를 붙이지 마세요. 따옴표까지 값으로 인식됩니다.
> - `.env`는 `.gitignore`에 포함되어 있어 커밋되지 않습니다. 절대 커밋하지 마세요.

선택 설정 (공고문 질문)

| 키 | 기본값 | 의미 |
|---|---|---|
| `RAG_SERVICE_ENABLED` | `false` | 공고문 질문에 Python RAG 서비스를 호출. 꺼져 있거나 응답이 없으면 DB 원문 발췌로 대체 |
| `RAG_SERVICE_URL` | `http://localhost:8000` | RAG 서비스 주소. 계약은 [docs/rag-service-contract.md](docs/rag-service-contract.md) |
| `RAG_SERVICE_TIMEOUT` | `15s` | RAG 응답 대기 한도. 프론트 로딩 기준과 같음. 넘으면 원문 발췌로 대체 |

## 3. DB 실행 (Docker)

**`backend` 폴더**에서 실행합니다.

```bash
docker compose up -d
docker compose ps        # postgres의 STATUS가 healthy 이면 OK
```

DB 종료: `docker compose down` (데이터 유지)
DB 초기화: `docker compose down -v` (데이터 삭제)

> pgAdmin(DB GUI)이 필요하면 `.env`에 `PGADMIN_EMAIL`, `PGADMIN_PASSWORD`를 추가하고
> `docker compose --profile tools up -d` → 브라우저에서 http://localhost:5050

## 4. 백엔드 실행

`application.yaml`이 `backend/.env`를 자동으로 읽기 때문에 **별도 환경변수 설정 없이 바로 실행**하면 됩니다.

### 방법 A. IntelliJ (권장)

1. IntelliJ에서 **`backend` 폴더**를 연다 (Gradle 프로젝트로 자동 인식)
2. Gradle 동기화가 끝날 때까지 기다린다 (오른쪽 아래 진행바)
3. `src/main/java/com/aiyouth/pjt/AiYouthPjtApplication.java` → `main` 옆 ▶ → **Run**

### 방법 B. 터미널

`backend` 폴더에서 실행합니다.

```bash
# Mac / Linux
./gradlew bootRun

# Windows PowerShell
.\gradlew.bat bootRun
```

> 특정 값만 내 PC에서 다르게 쓰고 싶으면 IntelliJ 실행 설정의 **Environment variables**나 OS 환경변수에 넣으세요.
> `.env`보다 우선 적용됩니다.

### 실행 확인

로그 마지막에 아래 문구가 나오면 성공입니다.

```
Started AiYouthPjtApplication in x.xxx seconds
```

서버 주소: http://localhost:8080
(아직 API가 없으면 404 / Whitelabel Error Page가 뜨는 것이 정상)

## 5. 자주 쓰는 명령어 (`backend` 폴더 기준)

| 작업 | Mac / Linux | Windows |
|---|---|---|
| 실행 | `./gradlew bootRun` | `.\gradlew.bat bootRun` |
| 빌드 | `./gradlew build` | `.\gradlew.bat build` |
| 테스트 | `./gradlew test` | `.\gradlew.bat test` |
| 빌드 결과 정리 | `./gradlew clean` | `.\gradlew.bat clean` |

## 6. 폴더 구조

```
backend/src/main/
├─ java/com/aiyouth/pjt/
│  ├─ controller/      API 엔드포인트
│  ├─ service/         비즈니스 로직
│  ├─ mapper/          MyBatis Mapper 인터페이스
│  ├─ model/           DB 테이블과 매핑되는 객체
│  ├─ dto/             request / response DTO
│  ├─ calculator/      계산 로직 (단위 테스트 대상)
│  ├─ llm/             LLM 연동
│  ├─ external/        외부 API 연동 (ODsay 등)
│  └─ global/          공통 설정, 예외 처리, 인증 등
└─ resources/
   ├─ application.yaml     공통 설정 (비밀값은 ${환경변수}로만 참조)
   ├─ mapper/              MyBatis XML (*.xml)
   ├─ db/migration/        Flyway SQL (V1__init.sql, V2__xxx.sql ...)
   └─ prompts/             LLM 프롬프트
```

> **Flyway 주의**: 이미 적용된 `V*.sql` 파일은 수정하지 말고, 변경 사항은 새 버전 파일(`V3__...sql`)로 추가하세요.
> 수정하면 다른 팀원 실행 시 checksum 오류가 납니다.

## 7. 첫집내비 세션과 후보 API

`POST /api/v1/sessions`에 `{}`를 보내면 `201`과 작업 쿠키를 받습니다. 세션 ID를 보관하고 이후 호출에 같은 쿠키를 사용합니다. 아래 경로의 `{sessionId}`는 생성 응답의 ID입니다.

| 기능 | 경로 | 요청 예시 |
|---|---|---|
| 1. 작업 복원 | `GET /api/v1/sessions/{sessionId}` | 저장된 프로필, 진행도, 후보, 현재 `revision` 반환 |
| 2. 주소 검색 | `POST /api/v1/sessions/{sessionId}/location-search` | `{"kind":"DESTINATION","query":"멀티캠퍼스 역삼"}` 또는 `{"kind":"HOME","query":"성수동2가 333-76"}` |
| 3. 프로필 저장 | `PATCH /api/v1/sessions/{sessionId}/profile` | 헤더 `If-Match: "0"`, 본문 `{"birthYear":1991,"birthdayPassedThisYear":false,"frequentPlace":{"locationId":"<목적지 검색 ID>"}}` |
| 7. 집 미리보기 | `POST /api/v1/sessions/{sessionId}/home-previews` | `{"inputRevision":1,"locationId":"<집 검색 ID>"}` |
| 8. 집 저장·교체 | `PUT /api/v1/sessions/{sessionId}/homes/1` | 헤더 `If-Match: "1"`, 본문 `{"previewId":"<미리보기 ID>","depositWon":10000000,"monthlyRentWon":600000,"maintenanceWon":null}` |
| 9. 집 삭제 | `DELETE /api/v1/sessions/{sessionId}/homes/1` | 저장 응답의 최신 `revision`을 `If-Match`로 보냄 |

- 검색 ID와 미리보기 ID의 유효기간은 각각 발급 후 15분입니다. 프로필 저장과 집 미리보기는 유효한 세션의 검색 ID가 만료되거나, 검색 종류·소유 세션이 다르거나, 선택 불가 항목이거나, 조회할 수 없으면 `404 LOCATION_SELECTION_UNAVAILABLE`을 반환합니다. 누락되거나 UUID 형식에 맞지 않는 요청 필드는 `422 FIELD_INVALID`입니다.
- 이 404를 받으면 같은 세션·쿠키를 유지하고 기존 검색 결과를 비운 뒤 재검색합니다. 새 `locationId`로 아직 저장하지 않은 프로필을 다시 제출하거나 새 집 미리보기를 요청할 수 있습니다. 검색과 실패한 저장·미리보기 요청은 `revision`을 올리지 않습니다.
- 집 저장에서 미리보기 ID가 만료되었거나 해당 미리보기의 목적지가 현재 목적지와 다르면 `409 PREVIEW_EXPIRED`를 반환합니다. 미리보기를 다시 받은 뒤 저장합니다. 세션이 만료되거나 쿠키 소유권을 확인할 수 없는 경우에는 `401 SESSION_EXPIRED`를 반환합니다.
- 경로 또는 시설 일부가 실패해도 미리보기는 `200`으로 응답합니다. 예를 들어 약국 조회에 실패하면 `facilities`의 약국 항목은 `status:"UNAVAILABLE"`이고 `warnings`에 `FACILITY_UNAVAILABLE`이 들어갑니다. 정상 조회에서 2km 안에 결과가 없으면 `NOT_FOUND`입니다.
- 수정 요청에 최신 `If-Match`가 없으면 `428 PRECONDITION_REQUIRED`, 값이 다르면 `412 REVISION_MISMATCH`입니다. 오래된 `inputRevision`으로 미리보기를 요청하면 `409 STALE_INPUT`입니다. 오류 응답은 아래 형식을 사용합니다.

```json
{"error":{"code":"LOCATION_SELECTION_UNAVAILABLE","message":"선택한 주소를 다시 검색해 주세요.","fields":[],"retryable":false},"requestId":"<요청 ID>"}
```

### 실제 연동 설정

- `KAKAO_REST_API_KEY`는 카카오 로컬 주소·장소·주변 시설 조회에 사용합니다. 키가 없거나 주소 조회에 실패하면 `503 UPSTREAM_UNAVAILABLE`을 반환합니다. [카카오맵 API](https://developers.kakao.com/docs/ko/kakaomap/common)
- `ODSAY_WEB_KEY`에는 백엔드 발신 공인 IP를 등록한 ODsay **서버 API 키**를 사용합니다. ODsay를 이용할 수 없으면 경로 결과가 부분적으로 비어 있을 수 있습니다. [ODsay 서버 플랫폼 안내](https://lab.odsay.com/community/boardView?seq=716)
- Vite 개발 서버는 `/api`를 백엔드의 8080 포트로 프록시합니다. 배포 시 프론트와 같은 출처에서 `/api/v1`에 접근하도록 구성합니다. 카카오 배경 지도가 필요하면 프론트에 `VITE_KAKAO_JS_KEY`를 설정합니다.
- 쿠키의 `Secure`는 기본적으로 켜져 있습니다. 로컬 HTTP 클라이언트가 쿠키를 전달하지 않으면 로컬 실행에 한해 `FIRSTHOME_SESSION_COOKIE_SECURE=false`를 사용합니다. 배포 환경의 공개 URL이 백엔드와 다른 경우 `FIRSTHOME_ALLOWED_ORIGINS`에 프론트의 공개 출처를 설정합니다. 세션 발급 후 유효기간의 기본값은 7일이며 `FIRSTHOME_SESSION_TTL_DAYS`로 조정합니다.

### 빈 값과 부분 결과

Flyway는 테이블을 생성합니다. 정책 카탈로그는 연결된 DB의 `policies` 테이블에 별도로 적재합니다. 아래 쿼리에서 `INCLUDE` 행이 없으면 지원 조회 목록이 비어 있습니다.

```sql
SELECT review_decision, count(*) FROM policies GROUP BY review_decision;
```

| 응답 필드 | 값이 비는 조건 |
|---|---|
| 지원 조회·가정의 `policies` | `INCLUDE` 정책만 내려갑니다. 대상이 없으면 `[]`입니다. |
| `summary.potentialTotalWon`, 정책별 `benefit`의 금액, 가정의 `delta`에 담기는 두 합계 | 현재 정책 판정·금액 산식이 보류되어 `null`입니다. 정책이 적재되면 항목은 `CHECK_NEEDED`, 합계는 `totalStatus: "UNKNOWN"`으로 내려갑니다. |
| 집 비교의 `homes[].rank`, `homes[].scores` 각 값, `recommendation.homeId`, `homes[].cost.monthlyAfterSupportWon` | 현재 점수 산식과 집별 지원 산식이 보류되어 `null`입니다. 응답은 `PARTIAL`이며 `rankedHomeIds`와 집별 `policyResults`는 `[]`입니다. 입력한 보증금·월세·관리비와 조회된 이동 시간·교통비는 응답에 반영합니다. 관리비 미확인은 `maintenanceWon: null`이며, 이때 월 주거비는 월세만 포함합니다. |
| 미리보기·비교의 `route.geometry` | ODsay 대중교통 경로는 `OK`여도 현재 `null`입니다. 시간·요금과 후보·목적지 마커는 표시할 수 있습니다. 도보 추정(`ESTIMATED`)은 직선 좌표가 있어 점선으로 표시합니다. 경로를 못 찾거나 조회할 수 없으면 `NOT_FOUND` 또는 `UNAVAILABLE`이고 시간·요금·좌표는 `null`입니다. |

### 지원·비교 기능에서 재사용

- 지원 판정에 사용할 프로필은 `PlanningSessions.getForInput(sessionId, token, inputRevision)`에서 가져옵니다. 비교에 사용할 후보와 조회 정보는 `ComparisonInputs.load(sessionId, token, inputRevision)`에서 가져옵니다. 조회 도중 목적지나 계약값이 바뀌면 `STALE_INPUT`이 반환됩니다. 호출은 트랜잭션 밖에서 하고, 비교 결과 저장 직전에 해당 저장 트랜잭션에서 버전을 다시 확인합니다.
- 수정 기능은 자신의 트랜잭션에서 `SessionState.lockOwned` → `Revisions.requireMatch` → 변경 저장 → `SessionState.advance` 순서로 사용합니다. 컨트롤러는 `SessionAuth.token`과 공통 오류 응답을 재사용할 수 있습니다.

## 8. 에러가 나면

| 메시지 | 원인 | 해결 |
|---|---|---|
| `Connection refused` | DB가 안 떠 있음 | Docker Desktop 켜기 → `backend` 폴더에서 `docker compose up -d` |
| `password authentication failed` | ① `.env`를 못 읽음 ② 비밀번호 불일치 | ① `backend/.env`가 있는지, `DB_PASSWORD`가 채워져 있는지, IntelliJ 실행 설정의 Working directory가 `backend` 폴더인지 확인 ② DB를 처음 띄운 뒤 `.env` 비밀번호를 바꿨다면 `backend` 폴더에서 `docker compose down -v` 후 다시 `up -d` (DB 데이터 초기화됨) |
| `database "..." does not exist` | DB 이름 불일치 | `.env`의 `DB_NAME`을 `ai_youth_pjt_db`로 맞추고 `docker compose down -v` → `up -d` |
| `Port 8080 was already in use` | 이미 실행 중 | 기존 실행을 ■로 중지 |
| `Port 5432 ... already allocated` | 로컬에 PostgreSQL이 이미 실행 중 | 로컬 PostgreSQL 서비스 중지 |
| `./gradlew: Permission denied` (Mac) | 실행 권한 없음 | `chmod +x gradlew` |
| `/usr/bin/env: 'sh\r'` / `bad interpreter` | `gradlew` 줄바꿈이 CRLF로 바뀜 | `git config core.autocrlf false` 후 `rm gradlew && git checkout -- gradlew` |
| `Unsupported class file major version` / 툴체인 오류 | JDK 17이 아님 | JDK 17 설치, IntelliJ Gradle JVM을 17로 설정 |
| 한글 깨짐 (Windows) | 인코딩 | `build.gradle`에 UTF-8 설정이 있으니 IntelliJ `File Encodings`도 UTF-8로 설정 |

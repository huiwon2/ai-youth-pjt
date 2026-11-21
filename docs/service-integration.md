# 첫집내비 통합 실행 및 배포

브라우저가 호출하는 주소는 모든 환경에서 `/api/v1`입니다. Spring Boot가 세션과 비교 스냅샷을 PostgreSQL에 저장하고, 공고문 질문은 Node.js RAG, 집 순위 계산은 Python 추천 서비스로 전달합니다.

## Vercel과 Render에 배포하기

네 서비스에는 같은 버전의 저장소를 지정합니다. 먼저 Render에서 PostgreSQL과 두 내부 서비스를 만들고, 내부 주소를 Spring에 입력합니다. Vercel의 API 전달 대상이 Spring의 공개 HTTPS 주소인지 확인한 뒤 Vercel 도메인을 Spring의 허용 출처에 등록합니다.

| 배포 대상 | Root Directory | 빌드 및 실행 | 환경변수 |
| --- | --- | --- | --- |
| Render · Private Service · RAG | `rag` | Node.js 24, `npm ci` → `npm start` | `HOST=0.0.0.0`, `PORT=8000`, `DATABASE_URL`에 PostgreSQL 내부 접속 URI. 모델 사용 시 `LLM_API_KEY`, 필요에 따라 `LLM_BASE_URL`, `LLM_MODEL` |
| Render · Private Service · 집 추천 | `recommend_house` | Python 3.10 이상, `pip install -r requirements.txt` → `python server.py` | `HOST=0.0.0.0`, `PORT=8001` |
| Render · Web Service · Spring | `backend` | Docker 런타임, 해당 디렉터리의 `Dockerfile` | 아래 Spring 설정 표 참고. 서버는 Render의 `PORT`로 기동 |
| Vercel · Vite | `frontend` | Node.js 24, `npm run build`, 출력 `dist` | 지도 사용 시 `VITE_KAKAO_JS_KEY` |

Render 서비스와 DB를 같은 워크스페이스·리전에 둡니다. Spring에서 두 내부 서비스를 호출할 주소는 각 서비스의 **Connect → Internal**에서 확인합니다. Vercel은 Spring의 공개 도메인에 접근합니다. [Render 내부 서비스](https://render.com/docs/private-services) · [Vercel 라우팅 설정](https://vercel.com/docs/rewrites)

| Spring 환경변수 | 설정값 |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://<내부 DB 호스트>:5432/<DB 이름>` |
| `DB_USER`, `DB_PASSWORD` | Flyway 마이그레이션과 앱 읽기·쓰기가 가능한 DB 계정 |
| `RAG_SERVICE_ENABLED`, `RAG_SERVICE_URL` | `true`, `http://<RAG 내부 호스트>:8000` |
| `RECOMMENDATION_SERVICE_URL` | `http://<추천 내부 호스트>:8001` |
| `FIRSTHOME_ALLOWED_ORIGINS` | `https://<Vercel 도메인>`. 커스텀 도메인이나 고정 프리뷰 주소도 사용한다면 쉼표로 구분 |
| `FIRSTHOME_SESSION_COOKIE_SECURE` | 배포 시 `true` (기본값) |
| `KAKAO_REST_API_KEY`, `ODSAY_WEB_KEY` | 주소·시설 검색 및 대중교통 조회용 서버 키 |

현재 [frontend/vercel.json](../frontend/vercel.json)은 API 요청을 `https://firsthome-demo-api.onrender.com`으로 전달하고 `/compare/:comparisonId` 등의 직접 접속을 처리합니다. Spring 도메인이 바뀌면 첫 번째 rewrite의 `destination`을 새 도메인에 맞춰 변경하고 배포합니다. 카카오 지도 JavaScript 키에는 실제 프론트 도메인을 등록합니다.

## 로컬에서 실행하기

Java 17, Node.js 24, Python 3.10 이상, PostgreSQL이 필요합니다. 아래 명령은 Windows PowerShell 기준이며 네 터미널 모두 저장소 루트에서 시작합니다. 기존 `.env`가 있으면 필요한 항목만 추가합니다.

1. **Spring과 DB** — `backend/.env.example`을 참고해 `backend/.env`에 DB 이름·계정·암호 및 외부 API 키를 채웁니다. 기존 설정에 `RAG_SERVICE_ENABLED=false`가 있으면 `true`로 바꿉니다. 로컬 HTTP 테스트에서는 `FIRSTHOME_SESSION_COOKIE_SECURE=false`를 사용할 수 있습니다. 아래 첫 번째 명령 묶음은 저장소의 PostgreSQL을 Docker로 실행하는 경우입니다.

   ```powershell
   cd backend
   docker compose up -d postgres
   .\gradlew.bat bootRun
   ```

   이미 PostgreSQL이 있다면 `DB_URL`, `DB_USER`, `DB_PASSWORD`를 해당 DB에 맞추고 `bootRun`을 실행합니다.

2. **RAG** — `rag/.env`에 Spring과 같은 DB를 가리키는 `DATABASE_URL=postgresql://<사용자>:<URL 인코딩된 암호>@localhost:5432/<DB 이름>`을 입력합니다. RAG에는 `rag_chunks` 조회 권한이 필요합니다. 모델을 사용할 경우 해당 파일에 모델 키를 입력합니다.

   ```powershell
   cd rag
   npm.cmd ci
   npm.cmd start
   ```

3. **집 추천** — 기존 추천 엔진을 HTTP로 실행합니다.

   **PowerShell**

   ```powershell
   cd recommend_house
   py -m venv "$env:TEMP\firsthome-recommend-venv"
   & "$env:TEMP\firsthome-recommend-venv\Scripts\python.exe" -m pip install -r requirements.txt
   & "$env:TEMP\firsthome-recommend-venv\Scripts\python.exe" server.py
   ```

   **Git Bash** (`MINGW`로 표시되는 터미널)

   ```bash
   cd recommend_house
   venv="$(cygpath -u "$TEMP")/firsthome-recommend-venv"
   py -m venv "$venv"
   "$venv/Scripts/python.exe" -m pip install -r requirements.txt
   "$venv/Scripts/python.exe" server.py
   ```

   이미 `recommend_house` 폴더에 있다면 `cd`는 건너뜁니다. 서버가 실행되면 터미널을 열어 두고 [추천 서비스 상태](http://localhost:8001/health)에서 `{"status":"ok"}`를 확인합니다.

4. **프론트** — 아래 명령을 실행한 뒤 브라우저에서 `http://localhost:5173`을 엽니다.

   ```powershell
   cd frontend
   npm.cmd ci
   npm.cmd run dev
   ```

## 동작 확인

- **세션** — ‘내 파일 만들기’를 누르고 내 조건 화면을 새로고침합니다. 개발자 도구의 Network에서 프론트 도메인의 세션 생성과 복원 요청이 성공하는지 확인합니다.
- **공고문 질문** — 검증된 정책의 이름과 지원 내용을 질문합니다. RAG는 카탈로그의 정책 키와 인용문을 PostgreSQL의 활성 `VERIFIED` 문단 및 원문 링크 또는 위치와 대조합니다. 모델 키가 없으면 확인된 원문을 발췌하며, 근거가 없으면 `NO_EVIDENCE`를 반환합니다. Flyway는 테이블을 생성하므로 운영 정책과 검증 문단은 별도로 적재해야 합니다. 로컬 카탈로그 색인은 기동 시 준비하며, 임베딩이 없는 환경에서는 어휘 검색을 사용합니다.
- **후보 비교** — 프로필과 후보 두 곳을 저장합니다. 관리비와 이동 시간이 확인되고 통근 한도 안에 드는 후보의 순위가 표시됩니다. 우선순위를 바꾸면 새 결과를 저장하고, 기존 비교 URL을 새로고침하면 저장 당시 결과를 읽습니다. 추천 엔진이 정한 순서를 그대로 사용하고 API 점수는 정수로 반올림합니다.
- **연결 문제** — RAG와 추천 서버는 각각 `GET /health`를 제공합니다. 이 경로는 프로세스 기동 여부만 확인합니다. DB 근거 조회는 실제 공고문 질문으로 확인합니다. 추천 서버에 연결할 수 없으면 비교표를 저장하고 `RANKING_UNAVAILABLE` 경고와 함께 순위를 보류합니다. 집별 지원 산식이 보류 중이므로 정상적으로 순위가 있어도 비교 상태는 `PARTIAL`일 수 있습니다.

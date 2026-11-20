# 백엔드 0단계: 실행 환경 구성

목표: `./gradlew bootRun` 또는 IntelliJ ▶ 실행 시 `Started AiYouthPjtApplication`이 나오게 만들기

## 손댈 파일 위치

```
ai-youth-pjt/                         ← 저장소 루트
├─ docker-compose.yml                  (이미 있음, 그대로)
├─ .env                                ① 만들기 (DB 이름·비밀번호)
├─ frontend/
└─ backend/                            ← IntelliJ로 연 폴더
   ├─ build.gradle                     ③ 맨 아래에 인코딩 설정 추가
   └─ src/
      ├─ main/
      │  ├─ java/com/aiyouth/pjt/
      │  │  └─ AiYouthPjtApplication.java   ⑥ 여기서 ▶ 실행
      │  └─ resources/
      │     ├─ application.properties  ② 지우고
      │     └─ application.yml         ② 새로 만들기
      └─ test/java/com/aiyouth/pjt/
         └─ AiYouthPjtApplicationTests.java ④ 지우기
```

⑤는 파일이 아니라 IntelliJ 실행 설정이다.
 
---

## ① 루트에 `.env` 만들기 + DB 띄우기

IntelliJ 아래쪽 **Terminal** 탭은 `backend` 폴더에서 시작하므로 루트로 한 칸 나간다.

```bash
cd ..
ls -a          # .env.example 이 있는지 확인
```

`.env.example`이 있으면 복사하고, 없으면 새로 만든다.

```bash
cp .env.example .env        # Windows PowerShell: Copy-Item .env.example .env
```

`.env` 내용 (비밀번호는 원하는 값으로): PM에게 문의

```
DB_NAME=ai_youth_pjt
DB_PASSWORD=원하는비밀번호
```

DB를 띄운다. Docker Desktop이 먼저 켜져 있어야 한다.

```bash
docker compose up -d
docker compose ps           # STATUS에 healthy 가 보이면 OK
cd backend                  # 다시 backend로
```

## ② `application.yml` 만들기

1. IntelliJ 왼쪽 Project 창에서 `backend > src > main > resources`를 펼친다.
2. `application.properties` 우클릭 → **Delete**
3. `resources` 우클릭 → **New → File** → `application.yml` 입력
4. 아래 내용을 붙여 넣는다.
```yaml
spring:
  application:
    name: ai-youth-pjt
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/ai_youth_pjt_db}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD}
 
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

> URL 끝의 `ai_youth_pjt_db`는 ①의 `.env`에 쓴 `DB_NAME`과 같아야 한다.

## ③ `build.gradle`에 인코딩 추가

1. `backend/build.gradle`을 연다.
2. 파일 **맨 아래**에 붙여 넣는다.
```groovy
tasks.withType(JavaCompile).configureEach { options.encoding = 'UTF-8' }
tasks.withType(Test).configureEach { systemProperty 'file.encoding', 'UTF-8' }
```

3. 에디터 오른쪽 위의 **코끼리 아이콘(Load Gradle Changes)**을 누른다.
> Windows의 Java 기본 인코딩(MS949) 때문에 한글이 깨지는 것을 막는 설정이다.

## ④ 기본 테스트 지우기

`backend > src > test > java > com.aiyouth.pjt > AiYouthPjtApplicationTests` 우클릭 → **Delete**

> 이 테스트는 DB 연결까지 하기 때문에 빌드할 때마다 실패할 수 있다. 테스트는 나중에 `calculator/` 단위 테스트로 다시 만든다.

## ⑤ 실행 설정에 비밀번호 넣기

Spring은 `.env` 파일을 자동으로 읽지 않으므로 IntelliJ에 직접 넣어 준다.

1. `AiYouthPjtApplication.java`를 열고 `main` 옆 ▶ → **Modify Run Configuration...**
2. **Environment variables** 칸에 입력 (칸이 안 보이면 **Modify options → Environment variables**)
```
DB_PASSWORD=①에서 쓴 비밀번호
```
3. **OK**
## ⑥ 실행

`main` 옆 ▶ → **Run**

Run 창 마지막 쪽에 아래 문구가 나오면 0단계 완료.

```
Started AiYouthPjtApplication in x.xxx seconds
```
 
---

## 에러가 나면

| 메시지 | 원인 | 해결 |
|---|---|---|
| `Connection refused` | DB가 안 떠 있음 | Docker Desktop 켜기 → `docker compose up -d` |
| `password authentication failed` | 비밀번호 불일치 | ⑤의 값과 `.env`를 맞춘다. 이미 다른 비밀번호로 DB를 띄웠다면 루트에서 `docker compose down -v` 후 다시 `docker compose up -d` |
| `database "ai_youth_pjt" does not exist` | DB 이름 불일치 | `.env`의 `DB_NAME`과 yml URL을 맞춘다 |
| `Port 8080 was already in use` | 이미 실행 중 | 기존 실행을 ■로 중지 |

## 마지막 확인

루트에서 `git status`를 실행해 `.env`가 목록에 **보이지 않는지** 확인한다. 보이면 루트 `.gitignore`에 `.env`를 추가한다.

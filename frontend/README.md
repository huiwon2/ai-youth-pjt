# React 개발용 VS Code 확장 추천(개발자 필독)

VS Code는 JSX/TSX 문법 강조, 자동완성, 타입 체크를 기본으로 지원하므로 **필수 확장은 없습니다.**
아래 확장은 설치하면 개발이 편해지는 것들입니다.

## 추천 확장

| 확장 | ID | 용도 |
|---|---|---|
| ESLint | `dbaeumer.vscode-eslint` | 코드 오류, Hooks 규칙 위반 등을 에디터에 표시 (Vite 템플릿에 ESLint 설정 포함) |
| Prettier | `esbenp.prettier-vscode` | 저장 시 코드 자동 포맷 |
| ES7+ React/Redux/React-Native snippets | `dsznajder.es7-react-js-snippets` | `rafce` 등 단축어로 컴포넌트 틀 빠르게 생성 (선택) |
| Tailwind CSS IntelliSense | `bradlc.vscode-tailwindcss` | Tailwind CSS 사용 시에만 필요 |

## 설치하기

### 방법 1: VS Code 화면에서

확장 탭(Mac `Cmd+Shift+X` / Windows `Ctrl+Shift+X`)을 열고 위 표의 ID로 검색해 **Install**을 누르세요.

### 방법 2: 터미널에서 한 번에

Mac 터미널, Windows PowerShell, 명령 프롬프트(cmd), Git Bash 어디서든 같은 명령어를 쓸 수 있습니다.

```bash
code --install-extension dbaeumer.vscode-eslint
code --install-extension esbenp.prettier-vscode
code --install-extension dsznajder.es7-react-js-snippets
# Tailwind 사용 시
code --install-extension bradlc.vscode-tailwindcss
```

### `code` 명령어를 찾을 수 없다고 나올 때

- **Mac**: VS Code에서 `Cmd+Shift+P` → **Shell Command: Install 'code' command in PATH**를 실행하세요.
- **Windows**: VS Code 설치 시 **"PATH에 추가"** 옵션이 기본으로 켜져 있어 보통 바로 됩니다. 안 되면 터미널을 완전히 닫았다가 다시 여세요. 그래도 안 되면 VS Code 설치 파일을 다시 실행해 **"PATH에 추가"**를 체크하고 재설치하거나, 방법 1로 설치하세요.

### 설치됐는지 확인하기

```bash
# Mac / Git Bash
code --list-extensions | grep -iE "eslint|prettier|snippets|tailwind"
```

```powershell
# Windows PowerShell
code --list-extensions | Select-String "eslint|prettier|snippets|tailwind"
```

네 개의 ID가 모두 출력되면 설치된 것입니다.

## 프론트엔드 실행하기

### 준비

- **Node.js 24**가 설치되어 있어야 합니다. `node -v`로 확인하세요.
- 버전이 다르면 아래처럼 맞추세요.
  - **Mac (nvm)**: `frontend` 폴더에서 `nvm install` → `nvm use`를 실행하면 `.nvmrc`에 적힌 24 버전을 사용합니다.
  - **Windows (nvm-windows)**: nvm-windows는 `.nvmrc`를 읽지 않으므로 버전을 직접 적어야 합니다. `nvm install 24` → `nvm use 24`를 실행하세요. `nvm use`가 실패하면 **관리자 권한으로 연 PowerShell**에서 다시 실행하세요.
  - nvm을 쓰지 않는다면 [nodejs.org](https://nodejs.org)에서 24 버전 설치 파일을 받아도 됩니다.
- 터미널은 Mac 터미널, Windows PowerShell, Git Bash, VS Code 터미널 중 어디서 해도 됩니다.

> **Windows PowerShell에서 `npm` 실행 시 "이 시스템에서 스크립트를 실행할 수 없으므로…" 오류가 날 때**
> PowerShell에서 아래 명령어를 한 번 실행한 뒤 터미널을 다시 여세요.
>
> ```powershell
> Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
> ```

### 처음 한 번

```bash
git clone https://github.com/huiwon2/ai-youth-pjt.git
cd ai-youth-pjt/frontend
npm install
cp .env.example .env       # Mac / Git Bash
npm run dev                # http://localhost:5173
```

`.env` 복사 명령어는 터미널마다 다릅니다.

| 터미널 | 명령어 |
|---|---|
| Mac 터미널 / Git Bash | `cp .env.example .env` |
| Windows PowerShell | `Copy-Item .env.example .env` |
| Windows 명령 프롬프트(cmd) | `copy .env.example .env` |

`.env`를 만든 뒤 `VITE_KAKAO_JS_KEY`에 카카오 JavaScript 키를 넣으세요. `.env`는 커밋하지 않습니다.

### 그 다음부터

```bash
git pull
npm install                # package.json이 바뀌었을 때만 필요
npm run dev
```

pull 후 `Cannot find module ...` 오류가 나면 누군가 라이브러리를 추가한 것이므로 `npm install`을 다시 실행하세요.

개발 서버는 Mac, Windows 모두 터미널에서 `Ctrl+C`로 종료합니다.

### 자주 쓰는 명령어

| 명령어 | 용도 |
|---|---|
| `npm run dev` | 개발 서버 실행 (http://localhost:5173) |
| `npm run lint` | ESLint 검사 |
| `npm run build` | 배포용 빌드 (`dist/` 생성) |
| `npm run preview` | 빌드 결과를 로컬에서 확인 |
| `npm install 패키지명` | 새 라이브러리 추가 |

> 새 라이브러리를 추가했다면 바뀐 `package.json`과 `package-lock.json`을 함께 커밋하세요. `node_modules/`는 커밋하지 않습니다.
>
> 백엔드 API(`/api`)는 `vite.config.ts`의 프록시 설정으로 `http://localhost:8080`에 전달됩니다. API를 함께 확인하려면 백엔드를 먼저 실행하세요.

## 첫집내비 첫 흐름 시연

백엔드 없이 `npm run dev`를 실행한 후 브라우저에서 http://localhost:5173/ 를 엽니다.

1. **내 파일 만들기**를 누릅니다.
2. 출생연도 1999, 올해 생일 지남, 직장인, 본인 명의 집 없음, 부모님과 주소 같음을 선택합니다.
3. 보증금 2000만 원과 월 소득 230만 원을 입력합니다. **멀티캠퍼스 역삼**을 검색하고 결과에서 선택합니다. 허용 이동 시간은 45분, 입주 시기는 다음 달을 선택합니다.
4. 진행도가 9/9일 때 **받을 수 있는 지원 보기**를 누릅니다. 520만 원 예시와 정책별 자격·접수 상태를 확인합니다. **35살이 되면?**을 누르면 280만 원 예시가 표시됩니다.

실제 백엔드를 연결할 때는 `.env.local`에 `VITE_FIRSTHOME_API_MODE=live`를 설정한 후 Vite를 다시 시작합니다. 개발 서버는 기존 `/api` 프록시로 백엔드의 8080 포트에 연결합니다. 배포 시에도 프론트와 같은 출처에서 `/api/v1`을 제공하고 이 환경 변수로 빌드합니다.

## 비교 결과의 후보 지도

결과 화면에서 **지도 열기**를 누르고 후보 이름이나 집 표시를 선택하면 해당 집의 경로와 주변 시설을 볼 수 있습니다. 키가 없는 시연 환경은 응답 좌표로 위치도를 표시합니다.

카카오 배경 지도를 사용하려면 카카오 개발자 앱에서 카카오맵을 활성화하고 실행 주소를 웹 플랫폼 도메인으로 등록합니다. 프론트의 `.env.local`에 `VITE_KAKAO_JS_KEY`를 설정한 뒤 Vite를 다시 시작합니다. 배포용 빌드에도 같은 환경 변수가 필요합니다.

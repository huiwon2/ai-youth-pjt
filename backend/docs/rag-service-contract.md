# 공고문 질문 Node.js RAG 서비스 계약 (API 6)

Spring Boot가 `POST /api/v1/sessions/{sessionId}/policy-questions`를 받으면 Node.js 서비스를 호출한다.
프롬프트·임베딩·검색·LLM 호출은 Node.js가 맡고, Java는 근거 확인과 최종 응답 형식을 맡는다.

## 호출

`POST {RAG_SERVICE_URL}/v1/policy-answers` · `Content-Type: application/json` · 응답 대기 `RAG_SERVICE_TIMEOUT`(기본 15초, 프론트 로딩 기준과 같음) · 연결 대기 3초

```json
{"question": "이 정책은 언제 신청하나요?", "policyKey": "GOV24-000000000001", "assessmentDate": "2026-11-21"}
```

| 필드 | 의미 |
|---|---|
| question | 사용자 질문 원문 (공백 제거, 최대 500자) |
| policyKey | 사용자가 보고 있던 정책. 없으면 `null` → 전체 검색 |
| assessmentDate | 판정 기준일(한국 날짜). 접수 기간 판단에 사용 |

사용자 프로필(나이·소득 등)은 보내지 않는다.

## 응답

```json
{
  "status": "ANSWERED",
  "answer": "월 20만 원씩 최대 12개월 받을 수 있어요.",
  "evidenceIds": ["f0000000-0000-0000-0000-00000000000f"],
  "helpLinks": [{"label": "복지로 상담", "url": "https://..."}]
}
```

| 필드 | 규칙 |
|---|---|
| status | `ANSWERED` / `EXCERPT` / `NO_EVIDENCE` / `OUT_OF_SCOPE` |
| answer | 사용자에게 보여줄 문장 |
| evidenceIds | 답변에 사용한 `rag_chunks.id` 목록 |
| helpLinks | 선택. 상담·원문 링크 |

## Java가 응답을 확인하는 방법

1. `evidenceIds` 중 `verification_status='VERIFIED'`, `is_active=true`이고 `source_url` 또는 `locator`가 있는 문단만 근거로 인정한다.
   근거 문장(excerpt)·링크는 Java가 DB에서 다시 읽어 만든다.
2. `ANSWERED`인데 인정된 근거가 없으면 → `NO_EVIDENCE`로 바꾼다.
3. `answer`의 숫자(금액·날짜·개월 수) 중 근거 원문에 없는 것이 있으면 → AI 문장을 버리고 원문 발췌(`SOURCE_EXCERPT`)로 답한다.
4. 연결 실패·시간 초과·5xx·형식 오류 → Java가 키워드로 검증된 문단을 찾아 `FALLBACK`으로 보여준다.

Node.js는 카탈로그에서 찾은 인용문을 같은 정책의 검증된 PostgreSQL 문단과 대조한다. 모델 키가 없으면 검증된 원문을 직접 발췌해 `EXCERPT`를 반환하고, Java는 이를 `SOURCE_EXCERPT`로 표시한다.

## 로컬 확인

```bash
# backend/.env
RAG_SERVICE_ENABLED=true
RAG_SERVICE_URL=http://localhost:8000
```

서비스별 실행 명령과 DB 환경변수는 [통합 실행 및 배포](../../docs/service-integration.md)를 참고한다.

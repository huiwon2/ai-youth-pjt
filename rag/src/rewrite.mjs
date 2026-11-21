// 질문 재작성: 상황 설명을 정책 용어 검색어로 바꿔 검색에 같이 써요.
// - RAG_QUERY_REWRITE=1 일 때만 켜져요 (기본 꺼짐).
// - 정해진 시간(기본 2초) 안에 응답이 없거나 실패하면 null → 원래 질문만으로 검색해요. 재시도하지 않아요.
// - 성공한 결과는 같은 질문에 다시 쓰도록 캐시해요 (프로세스 메모리 + SQLite).
import { createHash } from 'node:crypto';

export const REWRITE_PROMPT_VERSION = 'rewrite-v1';

const instructions = `당신은 한국 청년·복지 정책 카탈로그의 검색어를 만드는 도우미입니다.
사용자 질문을 정책 카탈로그에서 찾기 좋은 검색어 한 줄로 바꾸세요.
- 상황 설명을 정책 용어로 바꾸세요. 예: 지원 분야(주거, 일자리, 자산형성, 창업, 문화, 돌봄), 대상(자립준비청년, 신혼부부, 미취업 청년, 저소득층), 지원 형태(수당, 장려금, 바우처, 대출, 이자 지원, 보증료 지원, 임대주택, 인턴십, 장학금, 적금·통장).
- 질문에 있는 자치구 이름, 대상, 묻는 금액 종류(월세, 응시료, 사업화 자금 등) 같은 단서는 그대로 남기세요.
- 실제 사업명은 확실할 때만 넣고, 없는 사업명이나 조건을 지어내지 마세요.
- 15~60자 정도의 명사 위주 키워드로 쓰고, 질문 문장이나 존댓말은 쓰지 마세요.`;

const memory = new Map();
const keyOf = (model, question) => createHash('sha256').update(`${REWRITE_PROMPT_VERSION}|${model}|${question}`).digest('hex');
const normalize = value => String(value || '').normalize('NFKC').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');

/** 질문에 카탈로그 정책명이 이미 들어 있는지 (연도·'사업/운영/모집' 꼬리 제외, 5글자 이상) */
export function mentionsPolicyName(question, policies) {
  const query = normalize(question);
  return policies.some(policy => {
    const name = normalize(String(policy.name).replace(/20\d{2}\s*년?도?/gu, '').replace(/\[[^\]]*\]|\([^)]*\)/gu, ''))
      .replace(/(?:지원사업|사업|운영|모집|공고|지원)$/u, '');
    return name.length >= 5 && query.includes(name);
  });
}

/**
 * @returns {Promise<{status: 'ok'|'cached'|'timeout'|'error', query?: string, ms: number}>}
 */
export async function rewriteQuery(chatClient, question, { timeoutMs = 2000, store } = {}) {
  const started = Date.now();
  const model = chatClient.config.chatModel;
  const key = keyOf(model, question);
  const cached = memory.get(key) ?? store?.getRewrite?.(key);
  if (cached) { memory.set(key, cached); return { status: 'cached', query: cached, ms: Date.now() - started }; }
  try {
    const response = await chatClient.fetcher(`${chatClient.baseUrl}/responses`, {
      method: 'POST', headers: { Authorization: `Bearer ${chatClient.config.apiKey}`, 'Content-Type': 'application/json' },
      signal: AbortSignal.timeout(timeoutMs),
      body: JSON.stringify({ model, store: false, instructions, input: question, temperature: 0, max_output_tokens: 120,
        text: { format: { type: 'json_schema', name: 'search_query', strict: true, schema: {
          type: 'object', additionalProperties: false, required: ['query'], properties: { query: { type: 'string' } } } } } }),
    });
    const result = await response.json();
    if (!response.ok) return { status: 'error', ms: Date.now() - started };
    const text = (result.output || []).flatMap(item => item.content || []).filter(item => item.type === 'output_text').map(item => item.text).join('');
    const query = String(JSON.parse(text).query || '').trim().slice(0, 200);
    if (!query) return { status: 'error', ms: Date.now() - started };
    memory.set(key, query);
    store?.saveRewrite?.(key, query);
    return { status: 'ok', query, ms: Date.now() - started };
  } catch (error) {
    const timeout = error?.name === 'TimeoutError' || error?.name === 'AbortError';
    return { status: timeout ? 'timeout' : 'error', ms: Date.now() - started };
  }
}

/** 테스트용: 메모리 캐시 비우기 */
export function clearRewriteMemory() { memory.clear(); }

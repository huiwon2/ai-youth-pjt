// RAG 응답을 백엔드(Spring) 공고문 질문 API(API 6)의 PolicyAnswer 형식으로 바꿔요.
// 원문 문장·숫자는 바꾸지 않고, 상태 이름·근거 모양·마크다운만 바꿔요.
// 상태 대응과 needs_clarification 처리는 팀 논의 전 임시안이에요.
import { NOTICES, buildEvidence } from './grounding.mjs';

// RAG 상태 10개 → API 상태 4개 (ANSWERED / NO_EVIDENCE / OUT_OF_SCOPE / FALLBACK)
export const BACKEND_STATUS = Object.freeze({
  answered: 'ANSWERED',
  partial: 'ANSWERED',
  no_related: 'NO_EVIDENCE',
  insufficient: 'NO_EVIDENCE',
  needs_clarification: 'OUT_OF_SCOPE',
  current_status: 'OUT_OF_SCOPE',
  personal: 'OUT_OF_SCOPE',
  out_of_scope: 'OUT_OF_SCOPE',
  blocked: 'FALLBACK',
  service_error: 'FALLBACK',
});

/**
 * @param response RAG `answer()` 결과 (`ask --json`과 같은 모양)
 * @returns {{status, answer, message, evidence, helpLinks, missingTopics, ragStatus}}
 *   evidence 원소는 API Evidence: {id, policyKey, title, excerpt, sourceUrl, locator}
 */
export function toBackendAnswer(response) {
  const ragStatus = typeof response?.status === 'string' ? response.status : 'service_error';
  const policies = new Map((response?.sources || []).map(source => [source.reference, source]));
  const evidence = [];
  const blocks = [];
  for (const section of response?.sections || []) {
    const policy = policies.get(section.source_number);
    if (!policy) continue; // 출처 정보가 없는 근거는 보내지 않아요
    const labels = new Map(buildEvidence([policy]).map(item => [item.path, item.label]));
    const lines = [policy.name];
    for (const item of section.evidence || []) {
      const label = labels.get(item.path) || item.path;
      evidence.push({
        id: `${policy.policy_key}:${item.path}`,
        policyKey: policy.policy_key,
        title: `${policy.name} · ${label}`,
        excerpt: item.quote,
        sourceUrl: policy.source_url || null,
        locator: item.path,
      });
      lines.push('', label, item.quote);
    }
    blocks.push(lines.join('\n'));
  }

  let status = BACKEND_STATUS[ragStatus] || 'FALLBACK';
  // 구체화 요청이어도 비교할 원문을 골랐다면 원문과 안내를 함께 보여줘요
  if (ragStatus === 'needs_clarification' && evidence.length) status = 'ANSWERED';
  // 근거 없는 답변은 답변으로 보내지 않아요
  if (status === 'ANSWERED' && !evidence.length) status = 'NO_EVIDENCE';

  const message = typeof response?.message === 'string' && response.message ? response.message : null;
  let answer;
  if (status === 'ANSWERED') {
    // RAG 답변과 같은 내용을 마크다운(###, **, >)과 출처 번호([n]) 없이 평문으로 조립해요
    answer = [...blocks, message].filter(Boolean).join('\n\n');
  } else {
    answer = message || NOTICES[ragStatus] || NOTICES.insufficient;
  }
  return {
    status,
    answer,
    message,
    evidence: status === 'ANSWERED' ? evidence : [],
    helpLinks: [], // 원문 링크는 evidence[].sourceUrl로 보내요 (프론트 근거 목록이 링크를 그려요)
    missingTopics: Array.isArray(response?.missingTopics) ? response.missingTopics : [],
    ragStatus,
  };
}

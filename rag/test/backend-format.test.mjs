import test from 'node:test';
import assert from 'node:assert/strict';
import { loadPolicies } from '../src/rag.mjs';
import { NOTICES, prepareGrounding, renderEvidenceSelection, noticeResponse } from '../src/grounding.mjs';
import { BACKEND_STATUS, toBackendAnswer } from '../src/backend-format.mjs';

// 실제 카탈로그와 근거 선택 결과(모델 응답 대신 고정 JSON)로 RAG 응답을 만들어 변환해요. API 호출 없음.
const records = loadPolicies(process.cwd()).policies;
const get = key => records.find(policy => policy.policy_key === key);
const KEY = 'GOV24-105100000001';

function answered(outcome = 'supported') {
  const context = prepareGrounding('근로장려금 지원 금액 알려줘', [get(KEY)]);
  const ids = context.evidence.filter(item => item.path === 'benefit.text').map(item => item.id);
  return renderEvidenceSelection(JSON.stringify({ outcome, evidence_ids: ids, related_sources: [1] }), context);
}

test('원문 근거는 API Evidence 형식으로, 답변은 마크다운 없는 평문으로 바꿔요', () => {
  const policy = get(KEY);
  const rag = answered();
  assert.ok(['answered', 'partial'].includes(rag.status));
  const result = toBackendAnswer(rag);

  assert.equal(result.status, 'ANSWERED');
  assert.equal(result.ragStatus, rag.status);
  const benefit = result.evidence.find(item => item.locator === 'benefit.text');
  assert.deepEqual(benefit, {
    id: `${KEY}:benefit.text`,
    policyKey: KEY,
    title: `${policy.name} · 지원 내용 원문`,
    excerpt: policy.benefit.text,
    sourceUrl: policy.source_url,
    locator: 'benefit.text',
  });
  // 근거 원문은 정책 필드 값과 글자까지 같아야 해요
  for (const item of result.evidence) {
    const value = item.locator.split('.').reduce((v, key) => v?.[key], policy);
    assert.equal(item.excerpt, value);
  }
  assert.ok(!/^#{1,6}\s/m.test(result.answer), '제목 기호 없음');
  assert.ok(!result.answer.includes('**'), '굵게 기호 없음');
  assert.ok(!/^>/m.test(result.answer), '인용 기호 없음');
  assert.ok(!/\[\d+\]/.test(result.answer), '출처 번호 없음');
  assert.ok(result.answer.startsWith(policy.name + '\n'));
  assert.ok(result.answer.includes(policy.benefit.text));
  assert.ok(result.answer.endsWith(rag.message));
  assert.deepEqual(result.helpLinks, []);
});

test('안내 상태는 API 상태로 바꾸고 RAG 안내 문구를 그대로 써요', () => {
  const expected = { no_related: 'NO_EVIDENCE', insufficient: 'NO_EVIDENCE', current_status: 'OUT_OF_SCOPE',
    personal: 'OUT_OF_SCOPE', out_of_scope: 'OUT_OF_SCOPE', needs_clarification: 'OUT_OF_SCOPE',
    blocked: 'FALLBACK', service_error: 'FALLBACK' };
  for (const [ragStatus, status] of Object.entries(expected)) {
    const result = toBackendAnswer(noticeResponse(ragStatus));
    assert.equal(result.status, status, ragStatus);
    assert.equal(result.answer, NOTICES[ragStatus], ragStatus);
    assert.deepEqual(result.evidence, [], ragStatus);
    assert.equal(result.ragStatus, ragStatus);
  }
  assert.equal(Object.keys(BACKEND_STATUS).length, 10);
});

test('구체화 요청이어도 고른 원문이 있으면 원문과 안내를 함께 보내요', () => {
  const rag = answered('needs_clarification');
  assert.equal(rag.status, 'needs_clarification');
  const result = toBackendAnswer(rag);
  assert.equal(result.status, 'ANSWERED');
  assert.ok(result.evidence.length > 0);
  assert.ok(result.answer.includes(NOTICES.needs_clarification));
});

test('근거 검증 실패·근거 없는 답변·형식이 깨진 응답은 답변으로 보내지 않아요', () => {
  const blocked = renderEvidenceSelection('bad JSON', prepareGrounding('근로장려금 지원 금액 알려줘', [get(KEY)]));
  assert.equal(toBackendAnswer(blocked).status, 'FALLBACK');

  const noEvidence = toBackendAnswer({ status: 'answered', answer: '### 제목\n> 근거 없는 문장', message: '안내', sections: [], sources: [] });
  assert.equal(noEvidence.status, 'NO_EVIDENCE');
  assert.deepEqual(noEvidence.evidence, []);

  const orphan = answered();
  const withoutSources = toBackendAnswer({ ...orphan, sources: [] });
  assert.equal(withoutSources.status, 'NO_EVIDENCE');

  for (const broken of [null, undefined, {}, { status: 42 }]) {
    const result = toBackendAnswer(broken);
    assert.equal(result.status, 'FALLBACK');
    assert.equal(result.answer, NOTICES.service_error);
  }
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { Store, retrieve, loadConfig, fusePolicyRankings, OpenAIClient } from '../src/rag.mjs';
import { rewriteQuery, mentionsPolicyName, clearRewriteMemory } from '../src/rewrite.mjs';

const make = (key, name, organization, summary) => ({ id: key, policy_key: key, name, source: 'YOUTHCENTER', organization, summary });
const corpus = [
  make('ASSET', '청년내일저축계좌', '보건복지부', '일하는 저소득 청년이 저축하면 정부지원금 월 10만원을 매칭 적립'),
  make('RENT', '서울시 청년 월세 지원', '서울특별시', '청년 1인 가구 월세 월 20만원 지원'),
  make('JOB', '청년 일자리 인턴십', '고용노동부', '미취업 청년 인턴 인건비 지원'),
];
const base = { embeddingModel: 'test', dimensions: 3, topK: 3, chatModel: 'test-chat', apiKey: 'k', baseUrl: 'https://api.openai.com/v1', rewriteTimeoutMs: 2000, rewriteFusionK: 10 };
const reply = query => Response.json({ output: [{ content: [{ type: 'output_text', text: JSON.stringify({ query }) }] }] });
const chat = (fetcher) => new OpenAIClient(base, fetcher);

test('질문 재작성은 기본으로 꺼져 있어요', () => {
  assert.equal(loadConfig(process.cwd(), {}).queryRewrite, false);
  assert.equal(loadConfig(process.cwd(), { RAG_QUERY_REWRITE: '1' }).queryRewrite, true);
  assert.equal(loadConfig(process.cwd(), { RAG_QUERY_REWRITE: '1' }).rewriteTimeoutMs, 2000);
});

test('꺼져 있으면 재작성 호출 없이 원래 질문으로만 검색해요', async () => {
  const store = new Store(':memory:');
  let calls = 0;
  try {
    store.sync(corpus);
    const trace = {};
    await retrieve(store, '돈을 모으고 싶어요', { ...base, queryRewrite: false }, { lexicalOnly: true, chatClient: chat(async () => { calls++; return reply('x'); }), trace });
    assert.equal(calls, 0);
    assert.equal(trace.rewrite, undefined);
  } finally { store.close(); }
});

test('정책명이 이미 들어 있으면 재작성을 건너뛰어요', async () => {
  assert.equal(mentionsPolicyName('청년내일저축계좌 지원금은 얼마야?', corpus), true);
  assert.equal(mentionsPolicyName('서울시 청년 월세는 얼마?', corpus), true);
  assert.equal(mentionsPolicyName('일하면서 저축하면 정부가 얼마 보태 줘?', corpus), false);
  const store = new Store(':memory:');
  let calls = 0;
  try {
    store.sync(corpus);
    const trace = {};
    await retrieve(store, '청년내일저축계좌 지원금은 얼마야?', { ...base, queryRewrite: true }, { lexicalOnly: true, chatClient: chat(async () => { calls++; return reply('x'); }), trace });
    assert.equal(calls, 0);
    assert.equal(trace.rewrite.status, 'skipped_named');
  } finally { store.close(); }
});

test('2초 안에 안 오거나 실패하면 원래 질문으로 검색해요', async () => {
  clearRewriteMemory();
  const slow = chat((url, init) => new Promise((resolve, reject) => {
    const timer = setTimeout(() => resolve(reply('늦은 답')), 5000);
    init.signal.addEventListener('abort', () => { clearTimeout(timer); reject(init.signal.reason); });
  }));
  const started = Date.now();
  const timedOut = await rewriteQuery(slow, '돈을 모으고 싶어요', { timeoutMs: 200 });
  assert.equal(timedOut.status, 'timeout');
  assert.ok(Date.now() - started < 1500);
  const failed = await rewriteQuery(chat(async () => Response.json({ error: { code: 'x' } }, { status: 500 })), '돈을 모으고 싶어요');
  assert.equal(failed.status, 'error');
  assert.equal(failed.query, undefined);

  const store = new Store(':memory:');
  try {
    store.sync(corpus);
    const trace = {};
    const results = await retrieve(store, '월세 지원 얼마', { ...base, queryRewrite: true, rewriteTimeoutMs: 200 }, { lexicalOnly: true, chatClient: slow, trace });
    assert.equal(trace.rewrite.status, 'timeout');
    assert.equal(results[0].policy.policy_key, 'RENT');
  } finally { store.close(); }
});

test('같은 질문은 캐시를 써서 다시 호출하지 않아요 (재시작 후에도 DB 캐시)', async () => {
  clearRewriteMemory();
  const store = new Store(':memory:');
  let calls = 0;
  const client = chat(async () => { calls++; return reply('자산형성 적금 정부지원금 매칭'); });
  try {
    const first = await rewriteQuery(client, '돈을 모으고 싶어요', { store });
    clearRewriteMemory(); // 프로세스 재시작 흉내
    const second = await rewriteQuery(client, '돈을 모으고 싶어요', { store });
    assert.equal(first.status, 'ok');
    assert.equal(second.status, 'cached');
    assert.equal(second.query, '자산형성 적금 정부지원금 매칭');
    assert.equal(calls, 1);
    // 실패는 캐시하지 않아요
    const failing = chat(async () => Response.json({}, { status: 500 }));
    await rewriteQuery(failing, '다른 질문', { store });
    assert.equal(store.getRewrite('nope'), undefined);
  } finally { store.close(); }
});

test('재작성 검색 결과를 원래 질문 결과와 합쳐요', async () => {
  clearRewriteMemory();
  const store = new Store(':memory:');
  try {
    store.sync(corpus);
    const trace = {};
    // 원래 질문만으로는 키워드가 거의 안 맞지만, 재작성 검색어가 정답(저축계좌)을 찾아요.
    const results = await retrieve(store, '일하면서 돈을 모으면 나라에서 얼마 보태 줘?', { ...base, queryRewrite: true }, {
      lexicalOnly: true, chatClient: chat(async () => reply('저소득 청년 자산형성 저축 정부지원금 매칭 적립')), trace });
    assert.equal(trace.rewrite.status, 'ok');
    assert.equal(results[0].policy.policy_key, 'ASSET');
  } finally { store.close(); }
});

test('합칠 때 원래 질문의 1위는 재작성이 엉뚱해도 위에 남아요', () => {
  const primary = [{ policy_key: 'A' }, { policy_key: 'B' }, { policy_key: 'C' }];
  const secondary = [{ policy_key: 'X' }, { policy_key: 'Y' }, { policy_key: 'A' }];
  const fused = fusePolicyRankings(primary, secondary, 10).map(row => row.policy_key);
  assert.equal(fused[0], 'A');
  assert.ok(fused.indexOf('B') < fused.indexOf('Y'));
});

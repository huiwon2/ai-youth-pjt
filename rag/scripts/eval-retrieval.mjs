// 채팅 호출 없이 검색 성능만 재요 (hit@1/3/5, MRR). 질문 임베딩은 .rag/query-embeddings.json에 캐시해요.
//   node scripts/eval-retrieval.mjs --suite synthetic_dev [--file retrieval.json] [--lexical] [--misses]
// 같은 정책명(연도 제외)·기관의 중복 레코드는 정답으로 인정해요.
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { loadConfig, loadPolicies, Store, OpenAIClient, retrieve, signature, policyText } from '../src/rag.mjs';
import { resolveSuiteDirectory } from '../src/evaluation.mjs';

const arg = (name, fallback) => { const i = process.argv.indexOf(name); return i >= 0 ? process.argv[i + 1] : fallback; };
const lexicalOnly = process.argv.includes('--lexical');
const config = loadConfig();
const directory = resolveSuiteDirectory(config.root, arg('--suite', ''));
const file = join(directory, arg('--file', existsSync(join(directory, 'retrieval.json')) ? 'retrieval.json' : 'questions.json'));
// '이 사업', '이 제도'처럼 무엇을 묻는지 알 수 없는 문항은 검색 평가에서 빼요.
const VAGUE = /이\s*(?:사업|제도|지원|프로그램|정책)(?:에서|을|를|으로|의|은|는|에|$)/u;
const loaded = JSON.parse(readFileSync(file, 'utf8')).filter(q => q.policyKeys?.length);
const questions = loaded.filter(q => !VAGUE.test(q.question));
const identity = text => String(text || '').normalize('NFKC').replace(/\[정정공고\]/g, '').replace(/20\d{2}년?\s*/g, '').replace(/[^\p{L}\p{N}]/gu, '').toLowerCase();
const all = loadPolicies(config.root).policies;
const byKey = new Map(all.map(p => [p.policy_key, p]));
const accepted = q => {
  const gold = q.policyKeys.map(key => byKey.get(key));
  // 같은 정책명·기관이거나, 정책명이 같고 정답 사실(금액 등)이 그 레코드 원문에도 있으면 같은 사업으로 인정해요.
  const factsMatch = p => (q.facts || []).filter(f => f.path).every(f => new RegExp(f.pattern, 'iu').test(policyText(p).normalize('NFKC')));
  return new Set([...q.policyKeys, ...all.filter(p => gold.some(g => identity(g.name) === identity(p.name)
    && (identity(g.organization) === identity(p.organization) || factsMatch(p)))).map(p => p.policy_key)]);
};

// 질문 임베딩 캐시 (모델·차원·주소별)
const cachePath = join(config.root, '.rag', 'query-embeddings.json');
const cache = existsSync(cachePath) ? JSON.parse(readFileSync(cachePath, 'utf8')) : {};
const sig = signature(config);
let client = null;
if (!lexicalOnly) {
  const missing = [...new Set(questions.map(q => q.question))].filter(text => !cache[`${sig}|${text}`]);
  if (missing.length) {
    const api = new OpenAIClient(config);
    for (let i = 0; i < missing.length; i += 64) {
      const batch = missing.slice(i, i + 64);
      (await api.embed(batch)).forEach((vector, j) => { cache[`${sig}|${batch[j]}`] = vector; });
    }
    writeFileSync(cachePath, JSON.stringify(cache));
  }
  // 재작성 질문처럼 캐시에 없는 문장은 그때 임베딩하고 캐시에 더해요.
  const api = new OpenAIClient(config);
  client = { embed: async texts => {
    const missingNow = texts.filter(text => !cache[`${sig}|${text}`]);
    if (missingNow.length) { (await api.embed(missingNow)).forEach((vector, j) => { cache[`${sig}|${missingNow[j]}`] = vector; }); dirty = true; }
    return texts.map(text => cache[`${sig}|${text}`]);
  } };
}
let dirty = false;
// RAG_QUERY_REWRITE=1이면 재작성 사용 (재작성 결과는 .rag 색인 DB에 캐시돼 반복 실행 비용이 거의 없어요)
const chatClient = config.queryRewrite ? new OpenAIClient(config) : null;
const rewriteStatus = {};

const store = new Store(join(config.root, '.rag', 'policies.sqlite'));
const rows = [];
try {
  for (const q of questions) {
    const trace = {};
    const results = await retrieve(store, q.question, { ...config, topK: 5 }, { client, lexicalOnly, chatClient, trace });
    if (trace.rewrite) rewriteStatus[trace.rewrite.status] = (rewriteStatus[trace.rewrite.status] || 0) + 1;
    const ok = accepted(q);
    const index = results.findIndex(r => ok.has(r.policy.policy_key));
    rows.push({ id: q.id, category: q.category, question: q.question, gold: byKey.get(q.policyKeys[0]).name, rank: index < 0 ? null : index + 1, top: results.slice(0, 3).map(r => r.policy.name) });
  }
} finally { store.close(); if (dirty) writeFileSync(cachePath, JSON.stringify(cache)); }

const summarize = list => ({ n: list.length,
  hit1: list.filter(r => r.rank === 1).length, hit3: list.filter(r => r.rank && r.rank <= 3).length, hit5: list.filter(r => r.rank).length,
  mrr: Number((list.reduce((s, r) => s + (r.rank ? 1 / r.rank : 0), 0) / (list.length || 1)).toFixed(3)) });
const summary = { all: summarize(rows) };
for (const category of new Set(rows.map(r => r.category))) summary[category] = summarize(rows.filter(r => r.category === category));
console.log(JSON.stringify({ file: file.replace(config.root, '.'), mode: (lexicalOnly ? 'lexical' : 'hybrid') + (config.queryRewrite ? `+rewrite(k=${config.rewriteFusionK})` : ''), rewrite: config.queryRewrite ? rewriteStatus : undefined, excludedVague: loaded.length - questions.length, ...summary }));
if (process.argv.includes('--misses')) for (const r of rows.filter(r => r.rank !== 1)) console.log(`${r.id} [${r.rank ?? '-'}] ${r.question}\n   정답: ${r.gold}\n   결과: ${r.top.join(' / ')}`);

import { createServer } from 'node:http';
import { join } from 'node:path';
import pg from 'pg';
import { loadConfig, loadPolicies, OpenAIClient, retrieve, signature, Store } from './rag.mjs';
import { prepareGrounding } from './grounding.mjs';

const config = loadConfig();
const store = new Store(join(config.root, '.rag', 'policies.sqlite'));
store.sync(loadPolicies(config.root).policies);
const index = store.stats(signature(config));
const model = config.apiKey ? new OpenAIClient(config) : null;
const lexicalOnly = !model || index.embedded !== index.chunks;
if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required for verified evidence lookup');
const db = new pg.Pool({ connectionString: process.env.DATABASE_URL, max: 4, connectionTimeoutMillis: 3000,
  statement_timeout: 4000 });
const empty = (status = 'NO_EVIDENCE', answer = '확인된 근거가 없어요.') => ({ status, answer, evidenceIds: [], helpLinks: [] });

async function answer({ question, policyKey, assessmentDate }) {
  const policies = policyKey ? [store.policy(policyKey)].filter(Boolean)
    : (await retrieve(store, question, config, { client: model, lexicalOnly, chatClient: model })).map(r => r.policy);
  const context = prepareGrounding(question, policies);
  if (context.response) return empty(context.response.status === 'out_of_scope' ? 'OUT_OF_SCOPE' : 'NO_EVIDENCE', context.response.message);
  const keys = context.policies.map(p => p.policy_key);
  const { rows } = await db.query(`SELECT id, source_key, content FROM rag_chunks
    WHERE source_key = ANY($1::text[]) AND is_active AND verification_status = 'VERIFIED'
    AND (source_url IS NOT NULL OR locator IS NOT NULL) ORDER BY source_key, chunk_index, id`, [keys]);
  const find = (key, quote) => rows.find(r => r.source_key === key && r.content.includes(quote));
  const verified = context.evidence.filter(e => find(e.policyKey, e.quote));
  if (!verified.length) return empty();
  if (!model) {
    const excerpt = verified.find(e => e.topics.some(topic => context.plan.topics.includes(topic)));
    if (!excerpt) return empty();
    return { status: 'EXCERPT', answer: excerpt.quote, evidenceIds: [find(excerpt.policyKey, excerpt.quote).id], helpLinks: [] };
  }
  const result = await model.answer(question, context.policies, assessmentDate);
  if (result.status === 'service_error') throw new Error('model unavailable');
  if (result.status === 'out_of_scope') return empty('OUT_OF_SCOPE', result.message);
  const cited = result.sections.flatMap(s => s.evidence.map(e => find(s.policyKey, e.quote)));
  if (!cited.length || cited.some(row => !row)) return empty();
  return { status: 'ANSWERED', answer: result.sections.map(s => s.explanation).join('\n\n') + '\n' + result.message, evidenceIds: [...new Set(cited.map(row => row.id))], helpLinks: [] };
}

const server = createServer(async (req, res) => {
  const send = (code, value) => {
    res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(value));
  };
  if (req.method === 'GET' && req.url === '/health') return send(200, { status: 'ok' });
  if (req.method !== 'POST' || req.url !== '/v1/policy-answers') return send(404, {});
  try {
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
      size += chunk.length;
      if (size > 8192) return send(413, {});
      chunks.push(chunk);
    }
    let q;
    try { q = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { return send(400, {}); }
    if (typeof q?.question !== 'string' || !q.question.trim() || q.question.length > 500 ||
      (q.policyKey != null && typeof q.policyKey !== 'string') || !/^\d{4}-\d{2}-\d{2}$/.test(q.assessmentDate)) return send(400, {});
    return send(200, await answer(q));
  } catch {
    return send(503, { error: 'RAG_UNAVAILABLE' });
  }
});
server.listen(Number(process.env.PORT || 8000), process.env.HOST || '127.0.0.1');

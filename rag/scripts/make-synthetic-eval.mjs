// 서울 색인 정책으로 금액 질문을 자동 생성해 dev / test 평가 세트를 만들어요.
//   node scripts/make-synthetic-eval.mjs [--count 180] [--seed seoul-v2]
// 결과: evaluation/synthetic_dev, evaluation/synthetic_test 각각
//   retrieval.json  검색 평가용 전체 문항
//   questions.json  앞 30개 (evaluate.mjs 전체 평가용, 기존 형식)
//   excluded.json / excluded.md  뺀 질문과 이유
// 거르는 기준
//   1. 원문 인용 불일치: 정답 금액이 benefit.text의 부분 문자열이 아니면 버려요(질문 생성 실패로 보고 목록에 남기지 않음).
//   2. 지시어: '이 사업', '이 프로그램'처럼 정책을 지시어로만 가리키면 빼요.
//   3. 특정 불가: 정답 정책을 모르는 판정 LLM이 질문만 보고 사업 하나를 특정할 수 없다고 하면 빼요.
//      판정에는 검색 결과를 보여 주지 않아요(검색기에 유리한 질문만 남는 편향 방지).
import { createHash } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { loadConfig, loadPolicies, applyRegionFilter, OpenAIClient } from '../src/rag.mjs';

const arg = (name, fallback) => { const i = process.argv.indexOf(name); return i >= 0 ? process.argv[i + 1] : fallback; };
const COUNT = Number(arg('--count', '180'));
const SEED = arg('--seed', 'seoul-v2');
const AMOUNT = /(\d[\d,.]*)\s*(억|천|백|만)?\s*원/u;
const DEMONSTRATIVE = /(?:이|그|해당)\s*(?:사업|제도|지원|프로그램|정책|프로젝트|대출|혜택|서비스|상품)(?:에서|을|를|으로|로|의|은|는|에|이|가|$|\s)|(?:내가|제가)\s*받(?:게\s*될|을)\s*(?:혜택|지원)/u;
// 서울 서비스 평가에 맞지 않는 서울 밖 지역 전제 ('세종시에 거주하는', '충남 지역 대학', '충주시의 …')
const OTHER_REGION = /(?:부산|대구|인천|광주|대전|울산|세종|제주)(?:시|광역시|특별자치시|특별자치도|도)?(?:에|에서|의|\s|지역)|(?:경기도|강원도?|충청[남북]도|충[남북]|전라[남북]도|전[남북]|경상[남북]도|경[남북])(?:에|에서|의|\s|지역)|[가-힣]{2,4}(?:시|군)(?:에|에서|의)\s/u;
const hash = text => createHash('sha256').update(text).digest('hex');
const programName = name => String(name).normalize('NFKC').replace(/20\d{2}년?\s*/gu, '').replace(/[^\p{L}\p{N}]/gu, '');

// '최대 20만원' → (?<![\d,.])20\s*만 · '2,533,290원' → (?<![\d,.])2[,.]?533[,.]?290\s*원
function amountPattern(quote) {
  const match = quote.match(AMOUNT);
  if (!match) return null;
  const number = match[1].replace(/[.,]$/u, '').split(/[,.]/u).join('[,.]?');
  return `(?<![\\d,.])${number}${match[2] ? `\\s*${match[2]}` : '\\s*원'}`;
}

const config = loadConfig();
const client = new OpenAIClient(config);
const policies = applyRegionFilter(loadPolicies(config.root).policies).kept
  .filter(p => AMOUNT.test(String(p.benefit?.text || '')));
const unique = [...new Map(policies.map(p => [programName(p.name) + '|' + p.organization, p])).values()]
  .sort((a, b) => hash(SEED + a.policy_key).localeCompare(hash(SEED + b.policy_key)))
  .slice(0, COUNT);

const output = async (request) => {
  const result = await client.post('responses', { model: config.chatModel, store: false, ...request });
  return JSON.parse((result.output || []).flatMap(item => item.content || []).filter(item => item.type === 'output_text').map(item => item.text).join(''));
};
const schema = (name, properties) => ({ format: { type: 'json_schema', name, strict: true, schema: {
  type: 'object', additionalProperties: false, required: Object.keys(properties), properties } } });

const generateInstructions = `당신은 정책 검색 서비스의 평가 문항을 만드는 도우미입니다. 주어진 정책 원문을 보고, 서울에 사는 사용자가 실제로 물어볼 법한 한국어 질문 한 개를 만드세요.
질문은 원문의 지원 금액(또는 한도)을 묻고, 답이 원문 amount_quote 안에 있어야 합니다.
질문만 읽어도 어떤 사업을 묻는지 특정할 수 있어야 합니다. 이 사업만의 단서(사업 고유의 용어·지원 형태, 대상, 운영 기관, 자치구 이름 등)를 하나 이상 넣으세요.
'이 사업', '이 프로그램', '이 제도', '해당 지원'처럼 지시어로 사업을 가리키지 마세요. '인턴십 급여는 얼마?'처럼 여러 사업에 똑같이 해당하는 일반 질문도 만들지 마세요.
style이 situational이면 공식 정책명을 쓰지 말고 사용자의 상황과 필요를 설명하되, 위의 구별 단서는 상황 속에 자연스럽게 넣으세요.
style이 named이면 정책명을 사람이 부르는 식으로 자연스럽게 줄이거나 바꿔 부르세요(공식 명칭 그대로 복사 금지).
자치구 사업이면 구 이름을 질문에 넣으세요. 원문에 없는 조건을 지어내지 마세요.
amount_quote는 benefit_text에서 금액이 들어 있는 부분을 글자 그대로 복사한 짧은 구절(5~60자)이어야 합니다.`;

const judgeInstructions = `당신은 정책 검색 평가 문항의 품질을 검사합니다. 질문 하나와, 출제자가 의도한 정책 정보가 주어집니다.
판정할 것은 단 하나입니다: 질문이 '어느 사업'을 묻는지 특정할 수 있는가? 질문이 금액을 묻는 형식인지는 판정과 무관합니다(모든 문항이 금액을 묻습니다).
- specific: 질문에 사업 이름(줄임말·별칭 포함)이 있거나, 사업 이름이 없더라도 대상·지원 형태·고유 용어·운영 기관·자치구 같은 단서가 그 사업을 가리킴.
  예) '서울시 청년수당은 월 얼마야?' → specific(사업 이름).  '공예 분야 청년 인턴십 지원금은?' → specific(공예 분야 인턴십).  '가족을 돌보는 청년에게 주는 자기돌봄비는 연 얼마?' → specific(자기돌봄비).
- ambiguous: 사업을 지시어로만 가리킴('이 프로그램', '해당 지원'), 또는 단서가 너무 일반적이라 성격이 다른 여러 사업이 똑같이 답이 됨, 또는 질문의 단서가 의도한 사업과 맞지 않음.
  예) '정부 인턴십 프로그램 급여는 얼마?' → ambiguous(어느 인턴십인지 없음).  '자립준비청년이 받는 지원금은 얼마?' → ambiguous(자립수당·정착금·사례관리비 등 여러 사업).
같은 사업의 다른 연도·다른 출처 게시물이 함께 해당하는 것은 ambiguous가 아닙니다.
reason은 한국어 한 문장으로 쓰세요.`;

const items = [], excluded = [];
let cursor = 0, dropped = 0;
async function worker() {
  while (cursor < unique.length) {
    const index = cursor++;
    const policy = unique[index];
    const style = index % 2 ? 'named' : 'situational';
    try {
      const { question, amount_quote: quote } = await output({ instructions: generateInstructions, max_output_tokens: 400,
        input: JSON.stringify({ style, name: policy.name, organization: policy.organization, summary: policy.summary,
          benefit_text: String(policy.benefit.text).slice(0, 1500), target_text: String(policy.eligibility?.target_text || '').slice(0, 600) }),
        text: schema('synthetic_question', { question: { type: 'string' }, amount_quote: { type: 'string' } }) });
      const pattern = amountPattern(quote || '');
      if (!question?.trim() || !quote || !policy.benefit.text.includes(quote) || !pattern) { dropped++; continue; }
      const item = { index, policy, style, question: question.trim(), quote, pattern };
      if (DEMONSTRATIVE.test(item.question)) { excluded.push({ ...item, reason: '지시어로만 정책을 가리킴', by: '규칙' }); continue; }
      const region = item.question.match(OTHER_REGION);
      if (region && !/서울/.test(region[0])) { excluded.push({ ...item, reason: `서울 서비스와 맞지 않는 서울 밖 지역 전제 ('${region[0].trim()}')`, by: '규칙' }); continue; }
      const verdict = await output({ instructions: judgeInstructions, max_output_tokens: 200,
        input: JSON.stringify({ question: item.question, intended_policy: { name: policy.name, organization: policy.organization, summary: policy.summary } }),
        text: schema('question_judgement', { verdict: { type: 'string', enum: ['specific', 'ambiguous'] }, reason: { type: 'string' } }) });
      if (verdict.verdict === 'ambiguous') { excluded.push({ ...item, reason: verdict.reason, by: 'LLM 판정' }); continue; }
      items.push(item);
    } catch { dropped++; }
  }
}
await Promise.all(Array.from({ length: 4 }, worker));
items.sort((a, b) => a.index - b.index);
excluded.sort((a, b) => a.index - b.index);

const isDev = item => parseInt(hash(SEED + item.policy.policy_key).slice(-2), 16) % 2 === 0;
const toQuestion = (item, i, prefix) => ({
  id: `${prefix}${String(i + 1).padStart(3, '0')}`, category: item.style === 'named' ? '합성-정책명' : '합성-상황',
  question: item.question, policyKeys: [item.policy.policy_key],
  facts: [{ label: item.quote, pattern: item.pattern, path: 'benefit.text', quote: item.quote }],
});
for (const [split, prefix] of [['dev', 'D'], ['test', 'T']]) {
  const inSplit = item => isDev(item) === (split === 'dev');
  const questions = items.filter(inSplit).map((item, i) => toQuestion(item, i, prefix));
  const removed = excluded.filter(inSplit).map(item => ({ question: item.question, policyKey: item.policy.policy_key, policy: item.policy.name, style: item.style, by: item.by, reason: item.reason }));
  const directory = join(config.root, 'evaluation', `synthetic_${split}`);
  mkdirSync(directory, { recursive: true });
  writeFileSync(join(directory, 'retrieval.json'), JSON.stringify(questions, null, 2) + '\n');
  if (questions.length >= 30) writeFileSync(join(directory, 'questions.json'), JSON.stringify(questions.slice(0, 30), null, 2) + '\n');
  writeFileSync(join(directory, 'excluded.json'), JSON.stringify(removed, null, 2) + '\n');
  const escape = text => String(text).replace(/\|/g, '/').replace(/\r?\n/g, ' ');
  writeFileSync(join(directory, 'excluded.md'), [`# synthetic_${split}에서 뺀 질문 (${removed.length}개)`, '',
    `생성 시드 ${SEED}. 정책을 특정할 수 없는 질문을 뺐어요. 규칙: 지시어('이 사업', '이 프로그램' 등)로만 가리킴. LLM 판정: 정답 정책을 모르는 상태에서 질문만으로 사업 하나를 특정할 수 없음.`, '',
    '| 질문 | 의도한 정책 | 걸러낸 방법 | 이유 |', '|---|---|---|---|',
    ...removed.map(r => `| ${escape(r.question)} | ${escape(r.policy)} | ${r.by} | ${escape(r.reason)} |`), ''].join('\n'));
  console.log(`${split}: ${questions.length}문항 남김 (전체 평가용 ${Math.min(30, questions.length)}), ${removed.length}문항 뺌`);
}
console.log(`대상 정책 ${unique.length}개 · 남김 ${items.length} · 애매해서 뺌 ${excluded.length} (규칙 ${excluded.filter(e => e.by === '규칙').length}, LLM 판정 ${excluded.filter(e => e.by !== '규칙').length}) · 생성 실패 ${dropped}`);

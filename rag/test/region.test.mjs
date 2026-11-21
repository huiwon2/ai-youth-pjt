import test from 'node:test';
import assert from 'node:assert/strict';
import { Store, retrieve, chunkPolicy, applyRegionFilter } from '../src/rag.mjs';
import { orgRegion, fieldRegion, regionDecision, filterSeoulPolicies, jurisdictionRegion, regionInText } from '../src/region.mjs';

const config = { embeddingModel: 'test-model', dimensions: 3, topK: 5 };
const make = (key, name, organization, extra = {}) => ({ id: key, policy_key: key, name, source: 'YOUTHCENTER', organization,
  summary: `${name} 청년 지원 사업`, ...extra });

test('다른 광역시·도와 시·군·구 기관은 빼요', () => {
  for (const org of ['대구광역시', '대구광역시 청년여성교육국 청년정책과', '경기도', '세종특별자치시', '충청남도', '부산 강서구',
    '전남광주통합특별시 북구', '수원시', '가평군', '경기도 수원시', '서산시', '부산진구', '인천도시공사', '재단법인경기도미래세대재단',
    '고양시청소년재단', '(재)상주시장학회', '목포인재육성재단']) {
    assert.equal(orgRegion(org).region, 'other', org);
  }
});

test('서울특별시·서울 자치구와 중앙부처·공공기관은 남겨요', () => {
  for (const org of ['서울특별시', '서울특별시 미래청년기획관 청년정책담당관', '서울특별시 강서구', '은평구', '서울시복지재단', '재단법인서울장학재단']) {
    assert.equal(orgRegion(org).region, 'seoul', org);
  }
  for (const org of ['통일부', '문화체육관광부', '국가보훈부', '한국토지주택공사', '주택도시기금', '국토교통부 주택기금과', '새만금개발청']) {
    assert.equal(orgRegion(org).region, 'national', org);
  }
});

test('"주택도시"처럼 시로 끝나는 일반 단어나 "시군"은 지역으로 보지 않아요', () => {
  for (const org of ['주택도시기금', '주택도시과', '국토교통부 국토도시실', '농림축산식품부 및 시군']) {
    assert.notEqual(orgRegion(org).region, 'other', org);
  }
});

test('지역 필드와 법정동 코드도 같이 봐요 (11로 시작하면 서울)', () => {
  assert.equal(fieldRegion({ region: '11680' }), 'seoul');
  assert.equal(fieldRegion({ eligibility: { zip_codes: ['1168000000', '2611000000'] } }), 'seoul');
  assert.equal(fieldRegion({ 지역: '27110' }), 'other');
  assert.equal(fieldRegion({ region: '전국' }), 'nationwide');
  assert.equal(fieldRegion({ name: '대구 청년' }), null); // 지역 필드가 아니면 보지 않아요
  // 중앙기관이어도 대상 지역이 서울 밖뿐이면 빼요
  assert.equal(regionDecision(make('a', '청년 지원', '국토교통부', { region: '대구광역시' })).keep, false);
  assert.equal(regionDecision(make('b', '청년 지원', '국토교통부', { zip: '27110,26110' })).keep, false);
  assert.equal(regionDecision(make('c', '청년 지원', '국토교통부', { zip: '11110,27110' })).keep, true);
  // 서울 기관은 지역 필드가 이상해도 남겨요
  assert.equal(regionDecision(make('d', '청년 지원', '서울특별시', { region: '부산' })).keep, true);
});

test('부서명만 있는 기관은 접수기관·정책명·원문 링크로 판정해요', () => {
  assert.equal(regionDecision(make('a', '아산시 청년의 날 행사', '일자리경제과')).keep, false);
  assert.equal(regionDecision(make('b', '청년 지원', '청년정책과', { application: { receiving_agency: '충남신용보증재단' } })).keep, false);
  assert.equal(regionDecision(make('c', '청년 지원', '청년정책과', { source_url: 'https://youth.chungnam.go.kr/x' })).keep, false);
  assert.equal(regionDecision(make('d', '서울 청년 마음건강', '청년정책담당관')).keep, true);
  assert.equal(regionDecision(make('e', '청년 지원', '청년정책담당관', { source_url: 'https://youth.seoul.go.kr/x' })).keep, true);
  // '예산', '고령'처럼 일반 단어와 겹치는 시·군 이름은 정책명에서 지역으로 보지 않아요
  assert.equal(regionDecision(make('f', '고령 청년 예산 지원', '청년정책과')).keep, true);
  const unknown = regionDecision(make('g', '청년 지원', '청년정책과'));
  assert.equal(unknown.keep, true);
  assert.equal(unknown.unknown, true);
});

test('정책명 괄호에 시행 지역이 있으면 중앙부처 사업이라도 빼요', () => {
  assert.equal(regionDecision(make('a', '청년도전지원사업 젊핑(대구시)', '고용노동부')).keep, false);
  assert.equal(regionDecision(make('b', '사회적 고립청년 지원(부산시 금정구)', '경제교통국')).keep, false);
  assert.equal(regionDecision(make('c', '청년도전지원사업(서울시)', '고용노동부')).keep, true);
  assert.equal(regionDecision(make('d', '(국토부) 26년 청년월세 지원사업', '국토교통부')).keep, true);
  // 위치 표시('고양')는 시행 지역이 아니에요
  assert.equal(regionDecision(make('e', '대학생 연합생활관(은행권,고양)', '교육부')).keep, true);
  // 중앙기관은 접수기관·원문 링크만으로 빼지 않아요 (전국 사업의 지역 게시물)
  assert.equal(regionDecision(make('f', '청년월세 한시 특별지원', '국토교통부', { application: { receiving_agency: '강원특별자치도' }, source_url: 'https://www.chungbuk.go.kr/x' })).keep, true);
});

test('필터는 원본 목록을 바꾸지 않고 빠진 개수를 알려 줘요', () => {
  const policies = [make('s', '서울 청년수당', '서울특별시'), make('d', '대구 청년월세', '대구광역시'), make('n', '청년도약계좌', '금융위원회')];
  const snapshot = JSON.stringify(policies);
  const logs = [];
  const result = applyRegionFilter(policies, message => logs.push(message));
  assert.deepEqual(result.kept.map(p => p.policy_key), ['s', 'n']);
  assert.deepEqual(result.removed.map(r => r.policy.policy_key), ['d']);
  assert.equal(JSON.stringify(policies), snapshot);
  assert.match(logs[0], /전체 3개 중 서울 밖 지자체 정책 1개 제외, 2개 남김/);
  assert.equal(filterSeoulPolicies([]).kept.length, 0);
});

const corpus = [
  make('SEOUL-1', '서울 청년월세 지원', '서울특별시 주택실 주택정책관'),
  make('DAEGU-1', '대구 청년월세 지원', '대구광역시 건축주택국 주택과'),
  make('DAEGU-2', '대구 청년 전월세 임차보증금 대출이자 지원', '대구광역시'),
  make('GYEONGGI-1', '경기도 청년 월세 지원', '경기도'),
  make('NATIONAL-1', '청년월세 한시 특별지원', '국토교통부'),
];

test('색인을 만들 때 서울 밖 정책을 빼고 로그로 개수를 보여 줘요', () => {
  const store = new Store(':memory:');
  const logs = [];
  try {
    store.sync(corpus, { log: message => logs.push(message) });
    assert.deepEqual(store.policies().map(p => p.policy_key).sort(), ['NATIONAL-1', 'SEOUL-1']);
    assert.equal(store.stats('x').policies, 2);
    assert.match(logs[0], /지역 필터\(색인\): 전체 5개 중 서울 밖 지자체 정책 3개 제외, 2개 남김/);
  } finally { store.close(); }
});

test('"대구 청년 지원 알려줘"에 대구 정책이 나오지 않아요', async () => {
  const store = new Store(':memory:');
  try {
    store.sync(corpus);
    const results = await retrieve(store, '대구 청년 지원 알려줘', config, { lexicalOnly: true });
    assert.ok(results.length > 0);
    assert.ok(results.every(row => !row.policy.policy_key.startsWith('DAEGU')), results.map(r => r.policy.name).join(', '));
  } finally { store.close(); }
});

test('필터 전에 만든 색인이 남아 있어도 검색 결과에서 한 번 더 빼요', async () => {
  const store = new Store(':memory:');
  const logs = [];
  try {
    // 예전 색인처럼 필터 없이 직접 적재
    const insertPolicy = store.db.prepare('INSERT INTO policies VALUES (?, ?)');
    const insertChunk = store.db.prepare('INSERT INTO chunks VALUES (?, ?, ?, ?)');
    for (const policy of corpus) {
      insertPolicy.run(policy.policy_key, JSON.stringify(policy));
      for (const chunk of chunkPolicy(policy)) insertChunk.run(chunk.hash, chunk.policyKey, chunk.title, chunk.text);
    }
    const results = await retrieve(store, '대구 청년월세 지원', config, { lexicalOnly: true, log: message => logs.push(message) });
    assert.ok(results.length > 0);
    assert.ok(results.every(row => ['SEOUL-1', 'NATIONAL-1'].includes(row.policy.policy_key)));
    assert.match(logs[0], /지역 필터\(검색\): 전체 5개 중 서울 밖 지자체 정책 3개 제외/);
  } finally { store.close(); }
});

test('부서명 기관은 요약의 서울 밖 지역 이름으로도 빼요 (인천 마이스업체 청년 인턴)', () => {
  const policy = make('a', '마이스업체 청년 인턴십 운영', '관광마이스과', { summary: '○인천 마이스업체 인턴십 인건비 및 교육비용 등 지원' });
  const decision = regionDecision(policy);
  assert.equal(decision.keep, false);
  assert.match(decision.why, /요약에 인천/);
  assert.equal(regionDecision(make('b', '청년 지원', '청년정책담당관', { summary: '세종시 청년의 사회참여 지원' })).keep, false);
  assert.equal(regionDecision(make('c', '세종 청년참여마일리지 운영', '청년정책담당관')).keep, false);
});

test('부서명 기관은 지원대상·신청방법의 관할 표현으로도 빼요 (충주시 청년 소상공인 골목경제)', () => {
  const policy = make('a', '청년 소상공인 골목경제 활성화 지원사업', '경제교통국', {
    summary: '청년 소상공인 자립기반 구축과 소규모 골목 상권 활성화 도모',
    eligibility: { target_text: '청년 소상공인으로 구성된 충주시 소재 골목 경제 공동체' } });
  const decision = regionDecision(policy);
  assert.equal(decision.keep, false);
  assert.match(decision.why, /충주시 소재/);
  assert.equal(regionDecision(make('b', '청년 공동체 활성화', '자치행정국', { application: { method_text: '방문 접수(서귀포시청 2층)' } })).keep, false);
});

test("정책명·요약에 '서울'이 함께 있으면 남겨요", () => {
  assert.equal(regionDecision(make('a', '청년 교류 프로그램', '청년정책담당관', { summary: '서울·인천 청년 교류 행사 운영' })).keep, true);
  assert.equal(regionDecision(make('b', '서울 청년 지방 일경험', '청년정책담당관', { summary: '서울 청년이 충남 기업에서 일경험' })).keep, true);
  assert.equal(regionDecision(make('c', '청년 지원', '청년정책과', { eligibility: { target_text: '서울시 거주 청년. 세종시 소재 기업 근무 가능' } })).keep, true);
});

test('확실한 중앙부처 정책은 지역 이름이 있어도 남겨요 (괄호 시행지역 제외)', () => {
  assert.equal(regionDecision(make('a', '부산 지역 청년 통일 교육', '통일부', { summary: '부산·울산 청년 대상 통일 교육' })).keep, true);
  assert.equal(regionDecision(make('b', '충북 메타버스지원센터 운영', '과학기술정보통신부')).keep, true);
  assert.equal(regionDecision(make('c', '경북관광기업지원센터 청년인턴지원사업', '문화체육관광부', { eligibility: { target_text: '경상북도 소재 관광기업' } })).keep, true);
  assert.equal(regionDecision(make('d', '청년도전지원사업(원주시)', '고용노동부')).keep, false);
});

test('지역이 아닌 일반 단어는 지역으로 보지 않아요', () => {
  assert.equal(jurisdictionRegion('고령자 거주 가구 우선 지원'), null);
  assert.equal(jurisdictionRegion('관내 거주 청년'), null);
  assert.equal(jurisdictionRegion('전국 시군 소재 기업'), null);
  assert.equal(regionInText('시군경상보조 사업'), null);
  assert.equal(regionInText('경기 침체 대응 청년 지원'), null);
  assert.equal(regionInText('세종문화회관 청년 공연'), null);
  assert.equal(regionDecision(make('a', '청년 예술 지원', '문화예술과', { summary: '세종문화회관에서 청년예술인 공연 지원' })).keep, true);
  assert.equal(regionDecision(make('b', '청년 지원', '청년정책과', { eligibility: { target_text: '고령자 거주 가구의 청년' } })).keep, true);
  assert.deepEqual(jurisdictionRegion('세종시 거주 청년'), { region: 'other', phrase: '세종시 거주' });
  assert.deepEqual(jurisdictionRegion('서울 강서구 거주'), { region: 'seoul', phrase: '서울 강서구 거주' });
});

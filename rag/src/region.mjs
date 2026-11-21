// 서울 서비스용 지역 필터. scripts/filter-seoul.mjs 의 orgRegion · fieldRegion · decide 판정을 옮기고 보강했어요.
//   남김: 서울특별시 · 서울 자치구 · 중앙부처/공공기관(통일부, 문화체육관광부, 국가보훈부, LH, 주택도시기금 …) · 전국 대상
//   뺌:   다른 광역시·도(대구광역시, 경기도, 세종특별자치시 …) 또는 다른 시·군·구(수원시, 가평군, 부산 강서구 …) 정책
// 파일은 읽거나 쓰지 않는 순수 함수만 둬요.

const SIDO_PREFIX = ['부산', '대구', '인천', '광주', '대전', '울산', '경기', '강원', '충청', '충북', '충남', '전라', '전북', '전남', '경상', '경북', '경남', '제주'];
const SEJONG = /^세종(시|특별자치시)/;
export const SEOUL_GU = ['강남구', '강동구', '강북구', '강서구', '관악구', '광진구', '구로구', '금천구', '노원구', '도봉구', '동대문구', '동작구', '마포구', '서대문구', '서초구', '성동구', '성북구', '송파구', '양천구', '영등포구', '용산구', '은평구', '종로구', '중구', '중랑구'];
// 다른 시에도 같은 이름이 있는 서울 자치구. 앞에 시·도 이름이 없으면 서울로 보되 판정 이유에 표시해요.
const AMBIGUOUS_GU = new Set(['중구', '강서구']);
// 서울 밖 시·군 이름. 기관명에 붙어 쓰인 경우(목포인재육성재단, 고양시청소년재단)와 정책명 속 '아산시'를 잡는 데 써요.
const SIGUN = ('수원 성남 의정부 안양 부천 광명 평택 동두천 안산 고양 과천 구리 남양주 오산 시흥 군포 의왕 하남 용인 파주 이천 안성 김포 화성 양주 포천 여주 연천 가평 양평 '
  + '춘천 원주 강릉 동해 태백 속초 삼척 홍천 횡성 영월 평창 정선 철원 화천 양구 인제 고성 양양 '
  + '청주 충주 제천 보은 옥천 영동 증평 진천 괴산 음성 단양 천안 공주 보령 아산 서산 논산 계룡 당진 금산 부여 서천 청양 홍성 예산 태안 '
  + '전주 군산 익산 정읍 남원 김제 완주 진안 무주 장수 임실 순창 고창 부안 목포 여수 순천 나주 광양 담양 곡성 구례 고흥 보성 화순 장흥 강진 해남 영암 무안 함평 영광 장성 완도 진도 신안 '
  + '포항 경주 김천 안동 구미 영주 영천 상주 문경 경산 의성 청송 영양 영덕 청도 고령 성주 칠곡 예천 봉화 울진 울릉 군위 '
  + '창원 진주 통영 사천 김해 밀양 거제 양산 의령 함안 창녕 남해 하동 산청 함양 거창 합천 제주 서귀포 기장 달성 강화 옹진 울주').split(' ');
// 정책명에 이름만 단독으로 나올 때는 일반 단어와 겹치는 이름(예산, 고령, 강화, 달성 …)을 지역으로 보지 않아요.
const SIGUN_COMMON_WORD = new Set('고성 보은 영동 장수 예산 고령 진도 음성 동해 부여 영광 강진 상주 공주 영양 이천 구리 인제 구미 진주 달성 강화 화성 광명 양주 성주 청도 안성 신안 남해 하동 사천 영주 광양 기장 정선 오산 하남'.split(' '));
// 원문 링크 도메인에 쓰이는 서울 밖 지자체 영문 이름 (lib.changwon.go.kr, jejuyouth.com …)
const OTHER_HOST_EXACT = new Set(('chuncheon wonju gangneung donghae taebaek sokcho samcheok hongcheon hoengseong yeongwol pyeongchang jeongseon cheorwon hwacheon yanggu inje yangyang '
  + 'cheongju chungju jecheon boeun okcheon yeongdong jincheon goesan eumseong danyang cheonan gongju boryeong asan seosan nonsan gyeryong dangjin geumsan buyeo seocheon cheongyang hongseong yesan taean '
  + 'jeonju gunsan iksan jeongeup namwon gimje wanju jinan muju jangsu imsil sunchang gochang buan mokpo yeosu suncheon naju gwangyang damyang gokseong gurye goheung boseong hwasun jangheung gangjin haenam yeongam muan hampyeong yeonggwang jangseong wando jindo shinan '
  + 'pohang gyeongju gimcheon andong gumi yeongju yeongcheon sangju mungyeong uiseong cheongdo goryeong seongju chilgok bonghwa uljin ulleung '
  + 'changwon jinju tongyeong sacheon gimhae miryang geoje yangsan uiryeong haman namhae hadong sancheong geochang seogwipo '
  + 'suwon seongnam anyang bucheon pyeongtaek ansan iansan goyang guri osan siheung gunpo uiwang hanam yongin paju icheon anseong gimpo yangju pocheon yeoju yeoncheon '
  + 'saha haeundae suyeong yeonje geumjeong sasang dongnae busanjin yeongdo gijang suseong dalseo dalseong namdong bupyeong yeonsu michuhol gyeyang ganghwa ongjin gwangsan yuseong daedeok ulju').split(' '));
const OTHER_HOST_PART = /busan|daegu|incheon|gwangju|daejeon|ulsan|sejong|gyeonggi|gangwon|chungbuk|chungnam|chungcheong|jeonbuk|jeonnam|gyeongbuk|gyeongnam|jeju/;
const OTHER_HOST_LABEL = new Set(['gg', 'gwd', 'cb21', 'jb', 'gb', 'gsnd']);
const INSTITUTION = /(재단|공단|공사|장학회|진흥원|센터|협회)$/;
// '주택도시기금', '주택도시과'처럼 '시'로 끝나는 일반 단어, '시군'(여러 시·군), '국군'을 지역으로 보지 않아요.
const NOT_PLACE = /(도시|특별시|광역시|자치시|통합특별시)$|^(시군|국군|육군|해군|공군)$/;
const NOT_GU = new Set(['연구', '지구', '기구', '도구', '가구', '항구', '요구', '입구', '출구']);
const CENTRAL = /(부|처|청|위원회|공사|공단|재단|은행|진흥원|연구원|협의회|대학교|기금|공제회|원)$/;
const DEPARTMENT = /(과|담당관|관|국|실|팀|단|소|센터|본부)$/;
// 지자체마다 있는 기관이라 이름만으로는 어느 지역인지 모르는 경우
const LOCAL_GENERIC = /^(농업기술원|보건의료원|미래농업교육원|문화예술진흥원|교무학생처|구청장|청장|시장|군수)$/;

// 문자·숫자가 아닌 것은 모두 구분자예요: 괄호, 따옴표, '○인천'의 ○, '부산·울산'의 · 등
const BRACKETS = /[^\p{L}\p{N}]+/u;
const tokensOf = text => normalize(text).split(BRACKETS).filter(Boolean);
const normalize = text => String(text ?? '').normalize('NFKC').replace(/\((재|사|주)\)|(재단|사단)법인/g, ' ').replace(/[,·/]/g, ' ').replace(/\s+/g, ' ').trim();
const isSido = token => SIDO_PREFIX.some(prefix => token.startsWith(prefix)) || SEJONG.test(token);
const sigunToken = token => /^[가-힣]{1,5}(시|군)(청)?$/.test(token) && !NOT_PLACE.test(token.replace(/청$/, ''));
const otherGuToken = token => /^[가-힣]{1,4}구(청)?$/.test(token) && !NOT_GU.has(token.replace(/청$/, '')) && !SEOUL_GU.includes(token.replace(/청$/, ''));
const embeddedSigun = token => INSTITUTION.test(token) && SIGUN.some(name => token.startsWith(name) && token.length > name.length + 2);

/** 기관명만 보고 판정: seoul | other | national | unknown(부서명만 있어 어느 지자체인지 모름) */
export function orgRegion(org) {
  const o = normalize(org);
  if (!o) return { region: 'unknown', why: '기관 정보 없음' };
  const tokens = o.split(' ');
  const first = tokens[0];
  if (first.startsWith('서울')) return { region: 'seoul', why: `기관이 서울 (${o})` };
  for (const token of tokens) {
    if (isSido(token)) return { region: 'other', why: `다른 광역시·도 기관 (${o})` };
    if (sigunToken(token) || embeddedSigun(token)) return { region: 'other', why: `다른 시·군 기관 (${o})` };
    if (otherGuToken(token)) return { region: 'other', why: `서울 밖 구 기관 (${o})` };
  }
  const gu = SEOUL_GU.find(name => first === name || first === `${name}청` || (first.startsWith(name) && INSTITUTION.test(first)));
  if (gu) return { region: 'seoul', why: AMBIGUOUS_GU.has(gu) ? `서울 ${gu}로 판단 (다른 시에도 같은 구 이름 있음: ${o})` : `서울 자치구 (${o})` };
  if ((DEPARTMENT.test(first) && !CENTRAL.test(first)) || LOCAL_GENERIC.test(first)) return { region: 'unknown', why: `부서명만 있음 (${o})` };
  return { region: 'national', why: `중앙부처·공공기관 (${o})` };
}

function walkRegionFields(value, out = [], key = '') {
  if (value == null) return out;
  if (Array.isArray(value)) { value.forEach(item => walkRegionFields(item, out, key)); return out; }
  if (typeof value === 'object') {
    for (const [k, v] of Object.entries(value)) walkRegionFields(v, out, /region|area|zip|sido|지역|법정동/i.test(k) ? k : key);
    return out;
  }
  if (key) out.push(String(value));
  return out;
}

/** region · 지역 · zip · 법정동 코드 같은 지역 필드 판정: seoul | nationwide | other | null(필드 없음) */
export function fieldRegion(policy) {
  const values = walkRegionFields(policy).filter(Boolean);
  if (!values.length) return null;
  const codes = values.join(',').match(/\b\d{5,10}\b/g) || [];
  if (codes.length) {
    const sido = new Set(codes.map(code => code.slice(0, 2)));
    if (sido.size >= 15) return 'nationwide';
    return sido.has('11') ? 'seoul' : 'other'; // 법정동 코드 11 = 서울특별시
  }
  const text = values.join(' ');
  if (/전국|제한\s*없/.test(text)) return 'nationwide';
  if (/서울/.test(text)) return 'seoul';
  if (normalize(text).split(' ').some(isSido)) return 'other';
  return null;
}

export function hostRegion(url) {
  let host = '';
  try { host = new URL(url).hostname.toLowerCase(); } catch { return null; }
  const labels = host.split('.');
  if (labels.some(label => label.includes('seoul'))) return 'seoul';
  if (labels.some(label => OTHER_HOST_EXACT.has(label) || OTHER_HOST_LABEL.has(label) || OTHER_HOST_PART.test(label))) return 'other';
  return null;
}

// 정책명 토큰이 시·군 이름이면 true: '아산시', '홍성형', '천안(天安)의', 단독 '홍성' (일반 단어와 겹치는 이름은 '…시/군'일 때만)
const sigunInText = token => SIGUN.some(name => {
  if (!token.startsWith(name)) return false;
  const rest = token.slice(name.length);
  if (/^(시|군)/.test(rest)) return true;
  return !SIGUN_COMMON_WORD.has(name) && (rest === '' || /^(형|[^가-힣])/.test(rest));
});

// 글 속 지역 이름: 서울이면 seoul, 다른 시·도나 시·군이면 other ('경기'는 경기도일 때만)
// strict: 시·군 이름은 '원주시', '가평군'처럼 시·군이 붙었을 때만 (중앙기관 정책명 괄호용. '(은행권, 고양)' 같은 위치 표시는 남겨요)
// 글 속 시·도 이름: '경기'는 경기도일 때만, '경상'은 경상남·북도일 때만('경상비', '시군경상보조'는 지역이 아니에요)
// '세종'은 단독이거나 '세종청년…', '세종시민…', '세종형…'일 때 지역으로 봐요('세종문화회관', '세종대왕'은 아님).
const sidoInText = token => (isSido(token) || token === '세종' || /^세종(?:청년|시민|형|지역)/u.test(token))
  && !/^경기(?!도)/u.test(token) && !/^경상(?![남북]|권)/u.test(token);
function textRegion(text, strict = false) {
  for (const token of tokensOf(text)) {
    if (token.startsWith('서울')) return { region: 'seoul', token };
    if (sidoInText(token) || (strict ? sigunToken(token) : sigunInText(token))) return { region: 'other', token };
  }
  return null;
}

// 지원대상·신청방법·지원내용처럼 긴 글에서는 관할을 분명히 밝힌 표현만 봐요: '충주시 소재', '세종시 거주', '○○군 관내', '충주시청'
// 시·군 이름은 반드시 '시/군'이 붙어야 해요('고령자 거주' 같은 오인 방지).
const SIDO_FORMS = '(?:부산|대구|인천|광주|대전|울산)(?:광역시|시)?|세종(?:특별자치시|시)?|경기도|강원(?:특별자치)?도|충청[남북]도|충[남북]|전라[남북]도|전북(?:특별자치도)?|전남|경상[남북]도|경[남북]|제주(?:특별자치도|도|시)?';
const JURISDICTION_TAIL = '(?:\\s*(?:소재|거주|관내|주민|지역\\s*(?:거주|소재))|청(?![가-힣]))';
const OTHER_JURISDICTION = new RegExp(`(?:${SIDO_FORMS}|(?:${SIGUN.join('|')})(?:시|군))${JURISDICTION_TAIL}`, 'u');
const SEOUL_JURISDICTION = new RegExp(`서울(?:특별시|시)?${JURISDICTION_TAIL}|서울\\s*(?:자치구|[가-힣]{1,3}구)\\s*(?:소재|거주)`, 'u');
export function jurisdictionRegion(text) {
  const value = String(text ?? '').normalize('NFKC');
  if (SEOUL_JURISDICTION.test(value)) return { region: 'seoul', phrase: value.match(SEOUL_JURISDICTION)[0] };
  const match = value.match(OTHER_JURISDICTION);
  return match ? { region: 'other', phrase: match[0].trim() } : null;
}

/** 글에서 처음 나오는 지역 이름 (서울이면 seoul, 서울 밖이면 other). 보고서·테스트용 */
export function regionInText(text) { return textRegion(text); }

/** 정책명 괄호에 적힌 시행 지역: '젊핑(대구시)', '고립청년 지원(부산시 금정구)' */
export function nameTagRegion(name) {
  for (const [, inner] of String(name ?? '').matchAll(/[([【〈《「]([^)\]】〉》」]+)[)\]】〉》」]/g)) {
    const found = textRegion(inner, true);
    if (found) return found;
  }
  return null;
}

/** 부서명만 있는 정책을 위해 접수기관 · 정책명 · 요약 · 원문 링크 · 관할 표현에서 지역 단서를 찾아요: seoul | other | null */
export function contextRegion(policy) {
  const agency = orgRegion(policy.application?.receiving_agency);
  if (agency.region === 'other' || agency.region === 'seoul') return { region: agency.region, why: `접수기관 ${normalize(policy.application.receiving_agency)}` };
  // 정책명·요약: '서울'이 함께 나오면 서울 정책으로 남겨요. 아니면 서울 밖 시·도·시·군 이름이 지역 뜻으로 나오는지 봐요.
  for (const [label, text] of [['정책명', policy.name], ['요약', policy.summary]]) {
    if (/서울/u.test(String(policy.name ?? '') + String(policy.summary ?? ''))) return { region: 'seoul', why: `${label}·요약에 서울` };
    const found = textRegion(text);
    if (found?.region === 'other') return { region: 'other', why: `${label}에 ${found.token}` };
  }
  const host = hostRegion(policy.source_url);
  if (host) return { region: host, why: `원문 링크 ${new URL(policy.source_url).hostname}` };
  // 긴 본문은 관할을 밝힌 표현만: '충주시 소재', '충주시청', '세종시 거주'
  const sections = (policy.eligibility?.target_sections || []).map(section => section?.text);
  for (const [label, text] of [['지원대상', policy.eligibility?.target_text], ['선정조건', policy.eligibility?.criteria_text], ['대상별 조건', sections.join('\n')],
    ['신청방법', policy.application?.method_text], ['지원내용', policy.benefit?.text]]) {
    const found = jurisdictionRegion(text);
    if (found) return { region: found.region, why: `${label}에 '${found.phrase}'` };
  }
  return { region: null };
}

/** 정책 하나를 서울 서비스에 남길지 판정해요. */
export function regionDecision(policy) {
  const org = policy.organization ?? '';
  const byOrg = orgRegion(org);
  if (byOrg.region === 'other') return { keep: false, why: byOrg.why };
  const byField = fieldRegion(policy);
  if (byOrg.region !== 'seoul' && byField === 'other') return { keep: false, why: `대상 지역이 서울 밖 (${normalize(org) || '기관 없음'})` };
  // 중앙부처 사업이라도 정책명 괄호에 다른 지역을 적은 지역 시행분이면 빼요: '청년도전지원사업 젊핑(대구시)'
  const tag = byOrg.region === 'national' ? nameTagRegion(policy.name) : null;
  if (tag?.region === 'other') return { keep: false, why: `${byOrg.why} · 정책명에 시행 지역 ${tag.token}` };
  if (byOrg.region === 'unknown') {
    const byContext = contextRegion(policy);
    if (byContext.region === 'other') return { keep: false, why: `${byOrg.why} · ${byContext.why}` };
    if (byContext.region === 'seoul') return { keep: true, why: `${byOrg.why} · ${byContext.why}` };
    return { keep: true, unknown: true, why: `${byOrg.why} · 지역 단서 없음 → 남김` };
  }
  return { keep: true, why: byOrg.why };
}

/** 정책 목록을 서울 서비스용으로 걸러요. 원본 배열은 바꾸지 않아요. */
export function filterSeoulPolicies(policies) {
  const kept = [], removed = [];
  let unknown = 0;
  for (const policy of policies) {
    const decision = regionDecision(policy);
    if (decision.keep) { kept.push(policy); if (decision.unknown) unknown++; }
    else removed.push({ policy, why: decision.why });
  }
  return { kept, removed, unknownKept: unknown };
}

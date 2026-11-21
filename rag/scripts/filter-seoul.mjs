#!/usr/bin/env node
/**
 * 서울 서비스용 정책 카탈로그 정리 · 서울특별시 "밖" 지자체 정책만 빼요.
 *
 *   남김: 서울특별시 · 서울 자치구 · 중앙부처/공공기관(통일부, 문화체육관광부, LH, 주택도시기금 …) · 전국 대상
 *   뺌:   다른 광역시·도(대구광역시, 경기도 …) 또는 다른 시·군(수원시, 가평군 …)이 시행하는 정책
 *
 * 실행 (저장소 맨 위 폴더에서)
 *   node scripts/filter-seoul.mjs            ← 미리보기만. 무엇이 빠지는지 evaluation/seoul-filter-report.md 로 저장
 *   node scripts/filter-seoul.mjs --apply    ← 실제로 빼기. 원본은 backup_before_seoul_filter/ 에 먼저 복사
 *
 * 대상 파일: 온통청년 · 정부24 · LH _정책카탈로그_DB적재용 .json / .csv (CSV는 JSON에서 뺀 정책과 같은 행을 빼요)
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.cwd();
const APPLY = process.argv.includes('--apply');
const FILES = ['온통청년', '정부24', 'LH'].map(n => `${n}_정책카탈로그_DB적재용`);

/* ---------- 지역 판정 ---------- */
const SIDO = ['부산', '대구', '인천', '광주', '대전', '울산', '세종', '경기', '강원', '충청', '충북', '충남', '전라', '전북', '전남', '경상', '경북', '경남', '제주'];
const SIDO_FULL = /(부산|대구|인천|광주|대전|울산)광역시|세종특별자치시|경기도|강원(특별자치)?도|충청[남북]도|전라[남북]도|전북특별자치도|경상[남북]도|제주(특별자치)?도/;
const SEOUL = /서울/;
const SEOUL_GU = ['강남구', '강동구', '강북구', '강서구', '관악구', '광진구', '구로구', '금천구', '노원구', '도봉구', '동대문구', '동작구', '마포구', '서대문구', '서초구', '성동구', '성북구', '송파구', '양천구', '영등포구', '용산구', '은평구', '종로구', '중구', '중랑구'];
// 시·군 이름 토큰 (수원시, 가평군, 창원시청 …). '주택도시' 같은 일반 단어는 제외
const SIGUN_TOKEN = /^[가-힣]{1,4}(시|군)(청)?$/;
const NOT_PLACE = /(도시|특별시|광역시|자치시)$/;

function orgRegion(org) {
  const o = String(org || '').replace(/\s+/g, ' ').trim();
  if (!o) return { region: 'unknown' };
  if (SEOUL.test(o.split(' ')[0]) || /^서울/.test(o)) return { region: 'seoul', why: `기관이 서울 (${o})` };
  const first = o.split(' ')[0];
  const sido = SIDO.find(s => first.startsWith(s));
  if (sido || SIDO_FULL.test(o)) return { region: 'other', why: `다른 광역시·도 기관 (${o})` };
  for (const t of o.split(' ')) {
    if (SIGUN_TOKEN.test(t) && !NOT_PLACE.test(t.replace(/청$/, '')) && !SEOUL.test(t)) return { region: 'other', why: `다른 시·군 기관 (${o})` };
  }
  if (SEOUL_GU.some(g => first.startsWith(g))) return { region: 'seoul', why: `서울 자치구 (${o})` };
  return { region: 'national', why: `중앙부처·공공기관 (${o})` };      // 통일부, 문화체육관광부, 한국토지주택공사 …
}

// 지역 관련 필드(region, area, 지역, zip …)가 있으면 같이 봐요. 기관이 중앙인데 대상 지역이 다른 시도뿐인 경우 대비
function walkRegionFields(o, out = [], key = '') {
  if (o == null) return out;
  if (Array.isArray(o)) { o.forEach(v => walkRegionFields(v, out, key)); return out; }
  if (typeof o === 'object') { for (const [k, v] of Object.entries(o)) walkRegionFields(v, out, /region|area|zip|sido|지역|법정동/i.test(k) ? k : key); return out; }
  if (key) out.push(String(o));
  return out;
}
function fieldRegion(p) {
  const vals = walkRegionFields(p).filter(Boolean);
  if (!vals.length) return null;
  const codes = vals.join(',').match(/\b\d{5,10}\b/g) || [];
  if (codes.length) {
    const sido = new Set(codes.map(c => c.slice(0, 2)));
    if (sido.has('11') || sido.size >= 15) return 'ok';                // 서울 포함 또는 전국
    return 'other';
  }
  const t = vals.join(' ');
  if (/전국|서울|제한\s*없/.test(t)) return 'ok';
  if (SIDO.some(s => t.includes(s))) return 'other';
  return null;
}

function decide(p) {
  const org = p.organization ?? p.org ?? p.기관 ?? p.주관기관 ?? '';
  const r = orgRegion(org);
  if (r.region === 'other') return { keep: false, why: r.why };
  const f = fieldRegion(p);
  if (r.region !== 'seoul' && f === 'other') return { keep: false, why: `대상 지역이 서울 밖 (${org || '기관 없음'})` };
  return { keep: true, why: r.why || '기관 정보 없음 → 남김' };
}

/* ---------- 파일 읽기·쓰기 ---------- */
function listOf(data) {
  if (Array.isArray(data)) return { arr: data, put: a => a };
  for (const k of ['items', 'policies', 'data', 'records', 'rows']) if (Array.isArray(data?.[k])) return { arr: data[k], put: a => ({ ...data, [k]: a }) };
  throw new Error('JSON 안에서 정책 목록(배열)을 찾지 못했어요');
}
const idOf = p => String(p.policy_key ?? p.id ?? p.external_id ?? '');

function parseCSV(text) {          // 큰따옴표·줄바꿈이 들어간 칸도 처리
  const rows = []; let row = [], cell = '', q = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (q) { if (c === '"') { if (text[i + 1] === '"') { cell += '"'; i++; } else q = false; } else cell += c; continue; }
    if (c === '"') q = true; else if (c === ',') { row.push(cell); cell = ''; }
    else if (c === '\n' || c === '\r') { if (c === '\r' && text[i + 1] === '\n') i++; row.push(cell); rows.push(row); row = []; cell = ''; }
    else cell += c;
  }
  if (cell !== '' || row.length) { row.push(cell); rows.push(row); }
  return rows;
}
const toCSV = rows => rows.map(r => r.map(v => /[",\n\r]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v).join(',')).join('\r\n') + '\r\n';

const report = [`# 서울 밖 지자체 정책 빼기 ${APPLY ? '(적용함)' : '(미리보기)'}`, '', '남김: 서울특별시·서울 자치구·중앙부처/공공기관·전국 대상 · 뺌: 다른 광역시·도, 다른 시·군 시행 정책', ''];
const backup = path.join(ROOT, 'backup_before_seoul_filter');
let total = 0, removed = 0;

for (const base of FILES) {
  const jf = path.join(ROOT, base + '.json');
  if (!fs.existsSync(jf)) { report.push(`## ${base}`, '- 파일 없음 → 건너뜀', ''); continue; }
  const raw = fs.readFileSync(jf, 'utf8').replace(/^﻿/, '');
  const data = JSON.parse(raw);
  const { arr, put } = listOf(data);
  const keep = [], drop = [];
  for (const p of arr) (decide(p).keep ? keep : drop).push(p);
  total += arr.length; removed += drop.length;
  report.push(`## ${base}`, `- 전체 ${arr.length}개 → 남김 ${keep.length}개 · 뺌 ${drop.length}개`, '');
  if (drop.length) {
    report.push('| 뺀 정책 | 기관 | 이유 |', '|---|---|---|');
    for (const p of drop) report.push(`| ${String(p.name ?? p.title ?? idOf(p)).replace(/\|/g, '/')} | ${String(p.organization ?? '').replace(/\|/g, '/')} | ${decide(p).why.replace(/\|/g, '/')} |`);
    report.push('');
  }
  // 경계 사례 확인용: 중앙기관으로 판정해 남긴 것 중 기관명이 지역처럼 보이는 것
  const check = keep.filter(p => /시|군|구|도/.test(String(p.organization ?? '').split(' ')[0]) && orgRegion(p.organization).region === 'national');
  if (check.length) {
    report.push(`<details><summary>남겼지만 한 번 볼 것 ${check.length}개 (기관명이 지역처럼 보임)</summary>`, '', ...check.slice(0, 80).map(p => `- ${p.name} · ${p.organization}`), '', '</details>', '');
  }
  if (APPLY) {
    fs.mkdirSync(backup, { recursive: true });
    fs.copyFileSync(jf, path.join(backup, base + '.json'));
    fs.writeFileSync(jf, JSON.stringify(put(keep), null, 2) + '\n', 'utf8');
    const cf = path.join(ROOT, base + '.csv');
    if (fs.existsSync(cf)) {
      fs.copyFileSync(cf, path.join(backup, base + '.csv'));
      const ctext = fs.readFileSync(cf, 'utf8'), bom = ctext.startsWith('﻿');
      const rows = parseCSV(ctext.replace(/^﻿/, ''));
      const head = rows[0] || [];
      const col = ['policy_key', 'id', 'external_id'].map(k => head.indexOf(k)).find(i => i >= 0);
      if (col === undefined) report.push(`- ⚠ ${base}.csv 에 policy_key/id 열이 없어 CSV는 그대로 뒀어요`);
      else {
        const dropIds = new Set(drop.map(p => String(p[head[col]] ?? '')));
        const out = [head, ...rows.slice(1).filter(r => r.length > 1 && !dropIds.has(r[col]))];
        fs.writeFileSync(cf, (bom ? '﻿' : '') + toCSV(out), 'utf8');
        report.push(`- ${base}.csv: ${rows.length - 1}행 → ${out.length - 1}행`, '');
      }
    }
  }
}
report.splice(3, 0, `**전체 ${total}개 중 ${removed}개를 ${APPLY ? '뺐어요' : '뺄 예정이에요'}.**`, '');
fs.mkdirSync(path.join(ROOT, 'evaluation'), { recursive: true });
fs.writeFileSync(path.join(ROOT, 'evaluation', 'seoul-filter-report.md'), report.join('\n'), 'utf8');
console.log(`전체 ${total}개 중 ${removed}개 ${APPLY ? '뺐어요 (원본: backup_before_seoul_filter/)' : '뺄 예정 (미리보기)'} → evaluation/seoul-filter-report.md`);

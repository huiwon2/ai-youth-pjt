// 질문 재작성 끔/켬 평가 결과(evaluation/rw_<세트>_off|on)를 표로 요약해요.
//   node scripts/summarize-rewrite-ab.mjs seoul dev test
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const stat = (rows, key) => {
  const values = rows.map(row => row[key]).filter(Number.isFinite);
  if (!values.length) return '-';
  const sorted = [...values].sort((a, b) => a - b);
  const avg = values.reduce((s, v) => s + v, 0) / values.length;
  return `${(avg / 1000).toFixed(2)}s / ${(sorted.at(-1) / 1000).toFixed(2)}s`;
};
const lines = ['| 세트 | 재작성 | 답변 통과 | 검색 1위 | 검색 상위5 | 검색 단계 평균/최대 | 전체 평균/최대 | 재작성 상태 |', '|---|---|---|---|---|---|---|---|'];
for (const set of process.argv.slice(2)) {
  for (const mode of ['off', 'on']) {
    const path = join('evaluation', `rw_${set}_${mode}`, 'results.json');
    if (!existsSync(path)) continue;
    const report = JSON.parse(readFileSync(path, 'utf8'));
    const s = report.summary;
    const statuses = {};
    for (const row of report.results) if (row.rewrite) statuses[row.rewrite.status] = (statuses[row.rewrite.status] || 0) + 1;
    lines.push(`| ${set} | ${mode === 'on' ? '켬' : '끔'} | ${s.answerCorrect}/${s.total} | ${s.hitAt1}/${s.retrievalTotal} | ${s.hitAt5}/${s.retrievalTotal} | ${stat(report.results, 'retrievalMs')} | ${stat(report.results, 'elapsedMs')} | ${Object.entries(statuses).map(([k, v]) => `${k} ${v}`).join(', ') || '-'} |`);
  }
}
console.log(lines.join('\n'));

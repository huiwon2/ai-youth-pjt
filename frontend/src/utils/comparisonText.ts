import type { Comparison, Session } from '../types/contracts'
import type { SavedPolicy } from '../stores/savedPolicies'
import { comparisonRows } from './comparisonRows'
import { jobs, parents, moving } from './profileLabels'
import { slotName } from './home'
import { money } from './money'

export function comparisonText(session: Session, result: Comparison, saved: SavedPolicy[]) {
  const p = session.profile
  const unknown = '미확인'
  const age = p.age && (p.age.exactYears ?? (p.age.minYears + '~' + p.age.maxYears))
  const income = p.income && (p.income.period === 'MONTHLY' ? '월 ' : '연 ') + money(p.income.amountWon)
  const winner = result.homes.find(home => home.homeId === result.recommendation.homeId)
  const priority = { BALANCED: '균형', COST: '돈', COMMUTE: '시간', NEIGHBORHOOD: '동네', CUSTOM: '직접 설정' }
  const lines = [
    '내 첫 집 파일',
    '', '[현재 내 조건]',
    '출생연도 · ' + (p.birthYear === null ? unknown : p.birthYear + '년') + (age === null ? '' : ' (만 ' + age + '세)'),
    '올해 생일 · ' + (p.birthdayPassedThisYear === null ? unknown : p.birthdayPassedThisYear ? '오늘 또는 이미 지났어요' : '아직 안 지났어요'),
    '직업·학업 상태 · ' + (p.employmentStatus ? jobs[p.employmentStatus] : unknown),
    '본인 명의 집 · ' + (p.ownsHome === null ? unknown : p.ownsHome ? '있어요' : '없어요'),
    '부모님과 주민등록 주소 · ' + (p.parentAddressStatus ? parents[p.parentAddressStatus] : unknown),
    '보증금으로 쓸 돈 · ' + money(p.availableCashWon),
    '수입 · ' + (income ?? unknown),
    '자주 가는 곳 · ' + (p.frequentPlace ? p.frequentPlace.name + ' · ' + p.frequentPlace.address : unknown),
    '허용 이동 시간 · ' + (p.commuteLimit?.type === 'LIMIT' ? p.commuteLimit.minutes + '분' : p.commuteLimit ? '제한 없음' : unknown),
    '입주 시기 · ' + (p.moveIn ? moving[p.moveIn.type] : unknown),
    '', '[직접 담은 지원]',
    ...(saved.length ? saved.map(item => item.name + ' · 담을 때 예상 금액 ' + money(item.amountWon) + ' (' + item.assessmentDate + ' 기준)') : ['담은 지원이 없어요.']),
    '', '[후보 집 비교 결과]', '비교 ID · ' + result.comparisonId, '판정 기준일 · ' + result.assessmentDate,
    '비교 우선순위 · ' + priority[result.priorityPreset] + ' (돈 ' + result.weights.cost + '% · 시간 ' + result.weights.commute + '% · 동네 ' + result.weights.convenience + '%)',
  ]
  if (!result.isCurrent || result.inputRevision !== session.revision) lines.push('저장된 이전 비교 결과예요.')
  lines.push(winner ? '추천 후보 · ' + winner.label : '추천 순위 보류', ...result.recommendation.reasons, ...result.recommendation.tradeoffs)
  if (result.homes.length && result.homes.every(home => home.cost.maintenanceWon === null)) lines.push('각 집의 관리비를 확인해 주세요.')
  for (const home of result.homes) {
    lines.push('', '후보 ' + slotName(home.slot) + ' · ' + home.label, home.location.address)
    for (const [label, format] of comparisonRows) lines.push(label + ' · ' + format(home))
    for (const policy of home.policyResults) lines.push('집별 지원 · ' + policy.policyKey + ' · ' + money(policy.benefit.potentialTotalWon) + ' · ' + policy.reason)
  }
  if (result.warnings.length) lines.push('', '[확인할 점]', ...result.warnings.map(warning => warning.message))
  return lines.join('\n').replace(/\r?\n/g, '\r\n')
}

import type { ComparisonHome } from '../types/contracts'
import { facilityText, travel } from './home'
import { money } from './money'

export const comparisonRows: [string, (home: ComparisonHome) => string][] = [
  ['보증금', h => money(h.cost.depositWon)],
  ['월세', h => money(h.cost.monthlyRentWon)],
  ['관리비', h => h.cost.maintenanceWon === null ? '미확인' : money(h.cost.maintenanceWon)],
  ['한 달 주거비', h => money(h.cost.monthlyHousingCostWon) + (h.cost.excludedUnknownMaintenance ? ' + 관리비 별도' : '')],
  ['지원 기간 중 한 달 주거비', h => money(h.cost.monthlyAfterSupportWon) + (h.cost.excludedUnknownMaintenance && h.cost.monthlyAfterSupportWon !== null ? ' + 관리비 별도' : '')],
  ['편도 이동시간', h => travel(h.route)],
  ['월 교통비 (참고)', h => money(h.cost.monthlyTransportFareWon)],
  ['주변 시설', h => h.facilities.map(facilityText).join(' / ') || '확인 필요'],
  ['종합 점수', h => h.scores.total === null ? '확인 필요' : h.scores.total + '점'],
  ['순위', h => h.rank == null ? '순위 보류' : h.rank + '위'],
]

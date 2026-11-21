import type { Facility, Route } from '../types/contracts'

export const slots = [1, 2, 3] as const
export const slotName = (slot: number) => ['A', 'B', 'C'][slot - 1]

export function travel(route: Route) {
  if (route.status === 'UNAVAILABLE') return '조회 실패'
  if (route.status === 'NOT_FOUND') return '경로 없음'
  if (route.durationSeconds === null) return '이동시간 확인 필요'
  const mode = route.mode === 'WALK' ? '도보 ' : route.mode === 'TRANSIT' ? '대중교통 ' : ''
  return mode + Math.ceil(route.durationSeconds / 60) + '분' + (route.status === 'ESTIMATED' ? ' (추정)' : '')
}

const categories = { SUBWAY: '지하철역', LARGE_MART: '대형마트', SUPERMARKET: '동네 슈퍼', CONVENIENCE_STORE: '편의점', PHARMACY: '약국', GYM: '헬스장', PARK: '공원', INTERNAL_MEDICINE: '내과' }
export function facilityText(item: Facility) {
  const title = categories[item.category]
  if (item.status !== 'FOUND') return title + (item.status === 'NOT_FOUND' ? ' · 반경 내 없음' : ' · 조회 실패')
  return (item.name ?? title) + (item.walkMinutes === null ? '' : ' · 도보 ' + item.walkMinutes + '분')
}

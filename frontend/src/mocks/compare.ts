import { ApiError } from '../api/errors'
import { money } from '../utils/money'
import type { Comparison, ComparisonAnswer, PriorityPreset, ComparisonHome, Home, HomePreview, HomePut, LocationSearch, Route, Session } from '../types/contracts'

const examples = [
  { id: 'demo-home-seongsu', name: '성수동 원룸', address: '서울 성동구 연무장길 76', lng: 127.057, lat: 37.543, minutes: 30, walk: false, station: '성수역', stationMinutes: 8, rent: 600000, fee: 70000, totalScore: 87, rank: 1, afterSupport: 470000 },
  { id: 'demo-home-yeoksam', name: '역삼동 원룸', address: '서울 강남구 테헤란로 152', lng: 127.036, lat: 37.500, minutes: 13, walk: true, station: '역삼역', stationMinutes: 5, rent: 900000, fee: 100000, totalScore: 77, rank: 2, afterSupport: 800000 },
  { id: 'demo-home-sillim', name: '신림동 원룸', address: '서울 관악구 신림로 340', lng: 126.930, lat: 37.484, minutes: 30, walk: false, station: '신림역', stationMinutes: 6, rent: 420000, fee: null, totalScore: null, rank: null, afterSupport: 220000 },
]

export function searchDemoHomes(query: string): LocationSearch {
  const items = examples.map(example => ({
    locationId: example.id, name: example.name, roadAddress: example.address, parcelAddress: null,
    longitude: example.lng, latitude: example.lat, selectable: true, reason: null,
  }))
  return {
    items: items.filter(item => (item.name + item.roadAddress).replace(/\s/g, '').includes(query.replace(/\s/g, ''))),
    dataSource: 'DEMO',
  }
}

type Example = typeof examples[number]
type PreviewRecord = { revision: number; preview: HomePreview }

function read<T>(key: string): T | null {
  const value = sessionStorage.getItem(key)
  return value ? JSON.parse(value) : null
}

function sampleRoute(session: Session, example: Example | undefined): Route {
  const known = session.profile.frequentPlace?.name === '멀티캠퍼스 역삼' && example
  return {
    status: known ? 'OK' : 'UNAVAILABLE', mode: known ? known.walk ? 'WALK' : 'TRANSIT' : null,
    source: known ? 'DEMO' : null, durationSeconds: known ? known.minutes * 60 : null,
    distanceMeters: null, oneWayFareWon: known ? known.walk ? 0 : 1550 : null,
    overLimitSeconds: null, segments: [], geometry: null, queriedAt: new Date().toISOString(),
  }
}

function facilities(example: Example | undefined): HomePreview['facilities'] {
  return example ? [{
    category: 'SUBWAY', status: 'FOUND', name: example.station, location: null,
    distanceMeters: null, walkMinutes: example.stationMinutes, walkTimeSource: 'DEMO', searchRadiusMeters: 2000,
  }] : []
}

export function createDemoCompareApi(restore: (id: string) => Session, persist: (session: Session) => Session) {
  const key = (id: string, type: string, ref: string) => 'firsthome:demo:' + id + ':' + type + ':' + ref

  function requireRevision(id: string, revision: number, status: 409 | 412) {
    const session = restore(id)
    if (session.revision !== revision) throw new ApiError(status, '작업이 바뀌었어요. 최신 조건을 다시 불러와 주세요.')
    return session
  }

  function saveHomes(session: Session, homes: Home[]) {
    return persist({
      ...session, revision: session.revision + 1, homes,
      progress: { ...session.progress, canCompare: session.progress.canEvaluate && homes.length >= 2 },
    })
  }

  function getResult(id: string, comparisonId: string): Comparison {
    const result = read<Comparison>(key(id, 'result', comparisonId))
    if (!result) throw new ApiError(404, '이 비교 결과를 찾을 수 없어요.')
    return result
  }

  return {
    async savePriority(id: string, revision: number, priorityPreset: PriorityPreset) {
      const session = requireRevision(id, revision, 412)
      const presets = {
        BALANCED: { cost: 40, commute: 35, convenience: 25 },
        COST: { cost: 60, commute: 25, convenience: 15 },
        COMMUTE: { cost: 20, commute: 60, convenience: 20 },
        NEIGHBORHOOD: { cost: 30, commute: 20, convenience: 50 },
      }
      return persist({ ...session, revision: revision + 1, preferences: { priorityPreset, weights: presets[priorityPreset] } })
    },
    async askComparison(id: string, comparisonId: string, revision: number, question: string): Promise<ComparisonAnswer> {
      requireRevision(id, revision, 409)
      const result = getResult(id, comparisonId)
      if (result.inputRevision !== revision) throw new ApiError(409, '현재 조건으로 다시 비교한 뒤 질문해 주세요.', 'STALE_COMPARISON')
      const response: ComparisonAnswer = {
        comparisonId, inputRevision: revision, status: 'NO_EVIDENCE', answerSource: null,
        answer: '이 질문에 답할 근거가 시연 결과에 없어요.', references: [],
      }
      const seongsu = result.homes.find(home => home.location.address === examples[0].address)
      const yeoksam = result.homes.find(home => home.location.address === examples[1].address)
      if (!(question.includes('성수') && question.includes('역삼') && /차이|차이가|비교/.test(question)) || !seongsu || !yeoksam) return response
      const lines: string[] = []
      const pair = [seongsu, yeoksam]
      const firstCost = seongsu.cost.monthlyHousingCostWon
      const secondCost = yeoksam.cost.monthlyHousingCostWon
      if (firstCost !== null && secondCost !== null && pair.every(home => !home.cost.excludedUnknownMaintenance)) {
        const diff = firstCost - secondCost
        lines.push(diff === 0 ? '두 집의 월 주거비는 같아요.' : (diff < 0 ? seongsu : yeoksam).label + '의 월 주거비가 ' + money(Math.abs(diff)) + ' 낮아요.')
        for (const home of pair) response.references.push({ homeId: home.homeId, field: 'cost.monthlyHousingCostWon', value: home.cost.monthlyHousingCostWon })
      }
      const firstTime = seongsu.route.durationSeconds
      const secondTime = yeoksam.route.durationSeconds
      if (firstTime !== null && secondTime !== null) {
        const diff = Math.ceil(firstTime / 60) - Math.ceil(secondTime / 60)
        lines.push(diff === 0 ? '편도 이동시간은 같아요.' : (diff < 0 ? seongsu : yeoksam).label + '의 편도 이동시간이 ' + Math.abs(diff) + '분 짧아요.')
        for (const home of pair) response.references.push({ homeId: home.homeId, field: 'route.durationSeconds', value: home.route.durationSeconds })
      }
      return lines.length ? { ...response, status: 'ANSWERED', answerSource: 'RULE', answer: lines.join(' ') } : response
    },
    async previewHome(id: string, revision: number, locationId: string): Promise<HomePreview> {
      const session = requireRevision(id, revision, 409)
      const example = examples.find(item => item.id === locationId)
      if (!example) throw new ApiError(422, '검색 결과에서 주소를 골라 주세요.')
      const route = sampleRoute(session, example)
      const preview: HomePreview = {
        previewId: crypto.randomUUID(), expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
        location: { name: example.name, address: example.address, longitude: example.lng, latitude: example.lat },
        route, facilities: facilities(example),
        warnings: route.status === 'UNAVAILABLE' ? [{ code: 'ROUTE_UNAVAILABLE', field: 'route', message: '이 목적지의 시연 이동시간은 준비되지 않았어요.' }] : [],
      }
      sessionStorage.setItem(key(id, 'preview', preview.previewId), JSON.stringify({ revision, preview } satisfies PreviewRecord))
      return preview
    },
    async putHome(id: string, revision: number, slot: Home['slot'], body: HomePut) {
      const session = requireRevision(id, revision, 412)
      const previewKey = key(id, 'preview', body.previewId)
      const record = read<PreviewRecord>(previewKey)
      if (!record || record.revision !== revision || Date.parse(record.preview.expiresAt) <= Date.now()) {
        throw new ApiError(409, '미리보기가 만료됐어요. 주소를 다시 선택해 주세요.', 'PREVIEW_EXPIRED')
      }
      const { location } = record.preview
      const home: Home = {
        homeId: crypto.randomUUID(), slot, location, label: body.label ?? location.address,
        depositWon: body.depositWon, monthlyRentWon: body.monthlyRentWon, maintenanceWon: body.maintenanceWon,
        housingType: body.housingType ?? null, exclusiveAreaM2: body.exclusiveAreaM2 ?? null, inputMethod: 'MANUAL',
      }
      const saved = saveHomes(session, [...session.homes.filter(item => item.slot !== slot), home].sort((a, b) => a.slot - b.slot))
      sessionStorage.removeItem(previewKey)
      return saved
    },
    async deleteHome(id: string, revision: number, slot: Home['slot']) {
      const session = requireRevision(id, revision, 412)
      const homes = session.homes.filter(home => home.slot !== slot)
      return homes.length === session.homes.length ? session : saveHomes(session, homes)
    },
    async compare(id: string, revision: number): Promise<Comparison> {
      const session = requireRevision(id, revision, 409)
      if (!session.progress.canEvaluate || session.homes.length < 2 || session.homes.length > 3) {
        throw new ApiError(422, '비교할 집을 2~3곳 저장해 주세요.')
      }
      const matching = session.homes.map(home => examples.find(example => example.address === home.location.address))
      const fixedExample = session.profile.frequentPlace?.name === '멀티캠퍼스 역삼' &&
        matching.some(example => example === examples[0]) && matching.some(example => example === examples[1]) &&
        new Set(matching).size === matching.length && session.homes.every((home, index) =>
          matching[index]?.rent === home.monthlyRentWon && matching[index]?.fee === home.maintenanceWon && home.depositWon === 10000000)
      const hasDemoScores = fixedExample && session.preferences.priorityPreset === 'BALANCED'
      const homes: ComparisonHome[] = session.homes.map((home, index) => {
        const example = matching[index]
        const route = sampleRoute(session, example)
        return {
          homeId: home.homeId, slot: home.slot, label: home.label, location: home.location, route, facilities: facilities(example),
          cost: {
            depositWon: home.depositWon, depositShortfallWon: null, monthlyRentWon: home.monthlyRentWon,
            maintenanceWon: home.maintenanceWon, monthlyHousingCostWon: home.monthlyRentWon + (home.maintenanceWon ?? 0),
            excludedUnknownMaintenance: home.maintenanceWon === null, monthlyAfterSupportWon: fixedExample ? example?.afterSupport ?? null : null,
            monthlyTransportFareWon: null,
          },
          policyResults: [], scores: { cost: null, commute: null, convenience: null, total: hasDemoScores ? example?.totalScore ?? null : null },
          rank: hasDemoScores ? example?.rank ?? null : null,
        }
      })
      const winner = homes.find(home => home.rank === 1)
      const result: Comparison = {
        comparisonId: crypto.randomUUID(), inputRevision: revision, assessmentDate: session.assessmentDate,
        calculationVersion: 'demo-2026-11-19', isCurrent: true, status: homes.every(home => home.rank !== null) ? 'COMPLETE' : 'PARTIAL',
        ...session.preferences, homes,
        recommendation: {
          homeId: winner?.homeId ?? null, reasons: winner ? ['월 주거비가 역삼동 원룸보다 33만 원 낮아요.'] : [],
          tradeoffs: winner ? ['역삼동 원룸은 편도 이동시간이 17분 짧아요.'] : [],
          rankedHomeIds: [homes.find(home => home.rank === 1), homes.find(home => home.rank === 2)].flatMap(home => home ? [home.homeId] : []),
        },
        warnings: [
          ...(homes.some(home => home.cost.excludedUnknownMaintenance) ? [{ code: 'MAINTENANCE_UNKNOWN', field: 'maintenanceWon', message: '관리비가 미확인인 집은 한 달 비용에 월세만 표시돼요.' }] : []),
          ...(!hasDemoScores ? [{ code: 'DEMO_SCORE_UNAVAILABLE', field: 'scores', message: '이 조합의 시연 점수와 순위는 준비되지 않았어요.' }] : []),
        ],
        calculatedAt: new Date().toISOString(),
      }
      sessionStorage.setItem(key(id, 'result', result.comparisonId), JSON.stringify(result))
      persist({ ...session, latestComparisonId: result.comparisonId })
      return result
    },
    async comparison(id: string, comparisonId: string): Promise<Comparison> {
      const session = restore(id)
      const result = getResult(id, comparisonId)
      return { ...result, isCurrent: result.inputRevision === session.revision }
    },
  }
}

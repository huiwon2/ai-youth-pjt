import { ApiError } from '../api/errors'
import { createDemoCompareApi, searchDemoHomes } from './compare'
import type { LocationSearch, Policy, PolicyAnswer, PolicyEvaluation, Profile, ProfilePatch, Session } from '../types/contracts'

const date = '2026-11-19'
const places: LocationSearch['items'] = [
  { locationId: 'demo-multicampus', name: '멀티캠퍼스 역삼', roadAddress: '서울 강남구 테헤란로 212', parcelAddress: null, longitude: 127.04, latitude: 37.50, selectable: true, reason: null },
  { locationId: 'demo-cityhall', name: '서울시청', roadAddress: '서울 중구 세종대로 110', parcelAddress: null, longitude: 126.98, latitude: 37.57, selectable: true, reason: null },
]
const emptyProfile: Profile = {
  birthYear: null, birthdayPassedThisYear: null, birthdayAnswerAsOfDate: null,
  employmentStatus: null, frequentPlace: null, availableCashWon: null, income: null,
  commuteLimit: null, moveIn: null, ownsHome: null, parentAddressStatus: null,
  age: null, normalizedMonthlyIncomeWon: null,
}
const required = [
  'birthYear', 'employmentStatus', 'frequentPlace', 'availableCashWon', 'income',
  'commuteLimit', 'moveIn', 'ownsHome', 'parentAddressStatus',
] as const

function persist(session: Session) {
  sessionStorage.setItem('firsthome:fixture:' + session.sessionId, JSON.stringify(session))
  return session
}

function restore(id: string): Session {
  const stored = sessionStorage.getItem('firsthome:fixture:' + id)
  if (!stored) throw new ApiError(404, '작업을 찾을 수 없어요. 홈에서 다시 시작해 주세요.')
  return JSON.parse(stored)
}

function policy(policyKey: string, name: string, monthlyWon: number | null, durationMonths: number | null, oneTimeWon: number | null, total: number | null): Policy {
  return {
    policyKey, name, group: 'CHECK_NEEDED', eligibilityStatus: 'CHECK_NEEDED', applicationStatus: 'UNKNOWN',
    conditions: [], benefit: { monthlyWon, durationMonths, oneTimeWon, potentialTotalWon: total },
    rounds: [], methodText: null, sourceUrl: null, evidence: [],
  }
}

function eligibilityForAge(age: Profile['age']): Policy['eligibilityStatus'] {
  if (age === null) return 'CHECK_NEEDED'
  if (age.minYears >= 19 && age.maxYears <= 34) return 'MET'
  if (age.maxYears < 19 || age.minYears > 34) return 'NOT_MET'
  return 'CHECK_NEEDED'
}

function evaluation(id: string, revision: number, scenario = false): PolicyEvaluation {
  const session = restore(id)
  if (session.revision !== revision) throw new ApiError(409, '내 조건을 다시 불러와 주세요.')
  if (!session.progress.canEvaluate) throw new ApiError(422, '내 조건을 먼저 완성해 주세요.')
  const national = policy('DEMO-NATIONAL', '국토부 청년월세 지원', 200000, 24, null, 4800000)
  const seoul = policy('DEMO-SEOUL', '서울시 청년월세 지원', 200000, 12, null, 2400000)
  seoul.group = 'UPCOMING'
  seoul.eligibilityStatus = 'MET'
  seoul.applicationStatus = 'UPCOMING'
  const moving = policy('DEMO-MOVING', '중개보수 및 이사비 지원', null, null, 400000, 400000)
  const other = policy('DEMO-UNKNOWN', '추가 주거 지원', null, null, null, null)
  const baseAge = session.profile.age
  const scenarioAge = { minYears: 35, maxYears: 35, exactYears: 35 }
  const age = scenario ? scenarioAge : baseAge
  const baseTotal = eligibilityForAge(baseAge) === 'NOT_MET' ? 2800000 : 5200000
  const status = eligibilityForAge(age)
  national.eligibilityStatus = status
  national.group = status === 'NOT_MET' ? 'UNAVAILABLE' : 'CHECK_NEEDED'
  national.conditions = [{ key: 'age', label: '연령', actual: age == null ? null : '만 ' + (age.exactYears ?? (age.minYears + '~' + age.maxYears)) + '세', expected: '만 19~34세', status, reason: '시연용 연령 판정', evidenceIds: [] }]
  if (status === 'NOT_MET') national.benefit = { monthlyWon: null, durationMonths: null, oneTimeWon: null, potentialTotalWon: 0 }

  return {
    inputRevision: revision, assessmentDate: date, calculationVersion: 'demo-2026-11-19',
    summary: {
      potentialTotalWon: status === 'NOT_MET' ? 2800000 : 5200000, totalStatus: 'PARTIAL',
      includedPolicyKeys: [status === 'NOT_MET' ? seoul.policyKey : national.policyKey, moving.policyKey],
      targetYear: 2027,
    },
    policies: [national, seoul, moving, other], calendar: [], checklist: [], evidence: [], conflicts: status === 'NOT_MET' ? [] : [{ includedPolicyKey: national.policyKey, excludedPolicyKey: seoul.policyKey, reason: '시연 금액에는 국토부 청년월세와 서울시 청년월세를 중복 반영하지 않았어요.', evidenceIds: [] }],
    delta: scenario ? { baseTotalWon: baseTotal, scenarioTotalWon: 2800000, addedPolicyKeys: baseTotal === 5200000 ? [seoul.policyKey] : [], removedPolicyKeys: baseTotal === 5200000 ? [national.policyKey] : [], changedPolicyKeys: [] } : null,
  }
}

export const demoApi = {
  ...createDemoCompareApi(restore, persist),
  async create(): Promise<Session> {
    return persist({
      sessionId: crypto.randomUUID(), assessmentDate: date, revision: 0, expiresAt: null,
      profile: emptyProfile, progress: { answeredCount: 0, requiredCount: 9, missingFields: [...required], canEvaluate: false, canCompare: false },
      homes: [], preferences: { priorityPreset: 'BALANCED', weights: { cost: 40, commute: 35, convenience: 25 } }, latestComparisonId: null,
    })
  },
  async restore(id: string) {
    return restore(id)
  },
  async search(id: string, query: string, kind: 'HOME' | 'DESTINATION' = 'DESTINATION'): Promise<LocationSearch> {
    restore(id)
    if (kind === 'HOME') return searchDemoHomes(query)
    return { items: places.filter(place => (place.name + place.roadAddress).replace(/\s/g, '').includes(query.replace(/\s/g, ''))), dataSource: 'DEMO' }
  },
  async save(id: string, revision: number, patch: ProfilePatch) {
    const session = restore(id)
    if (session.revision !== revision) throw new ApiError(412, '작업이 변경되었어요. 다시 불러와 주세요.')
    const { frequentPlace, moveIn, ...values } = patch
    const profile = { ...session.profile, ...values }
    const place = places.find(item => item.locationId === frequentPlace?.locationId)
    if (frequentPlace && !place) throw new ApiError(422, '검색 결과에서 목적지를 골라 주세요.')
    if (frequentPlace !== undefined) {
      profile.frequentPlace = place ? { name: place.name, address: place.roadAddress!, longitude: place.longitude!, latitude: place.latitude! } : null
    }
    if (moveIn !== undefined) {
      const months = {
        THIS_MONTH: ['2026-11', '2026-11'], NEXT_MONTH: ['2026-12', '2026-12'],
        WITHIN_THREE_MONTHS: ['2026-11', '2027-02'], UNDECIDED: [null, null],
      }
      const [startMonth, endMonth] = moveIn ? months[moveIn.type] : [null, null]
      profile.moveIn = moveIn && { ...moveIn, asOfDate: date, startMonth, endMonth }
    }
    if (patch.birthdayPassedThisYear !== undefined) profile.birthdayAnswerAsOfDate = patch.birthdayPassedThisYear === null ? null : date
    else if (profile.birthYear !== session.profile.birthYear) {
      profile.birthdayPassedThisYear = null
      profile.birthdayAnswerAsOfDate = null
    }
    const age = profile.birthYear === null ? null : 2026 - profile.birthYear
    const birthday = profile.birthdayPassedThisYear
    profile.age = age === null ? null : {
      minYears: Math.max(0, birthday === true ? age : age - 1),
      maxYears: Math.max(0, birthday === false ? age - 1 : age),
      exactYears: birthday === null ? null : Math.max(0, birthday ? age : age - 1),
    }
    profile.normalizedMonthlyIncomeWon = profile.income && Math.floor(profile.income.amountWon / (profile.income.period === 'ANNUAL' ? 12 : 1))
    const missingFields = required.filter(field => profile[field] === null)
    return persist({
      ...session, profile, revision: revision + 1,
      progress: { answeredCount: 9 - missingFields.length, requiredCount: 9, missingFields, canEvaluate: missingFields.length === 0, canCompare: missingFields.length === 0 && session.homes.length >= 2 },
    })
  },
  async evaluate(id: string, revision: number) {
    return evaluation(id, revision)
  },
  async scenario(id: string, revision: number) {
    return evaluation(id, revision, true)
  },
  async ask(id: string, revision: number, question: string): Promise<PolicyAnswer> {
    evaluation(id, revision)
    const prepared = /신청.*(언제|일정)|언제.*신청/.test(question)
    return {
      inputRevision: revision, assessmentDate: date,
      status: prepared ? 'FALLBACK' : 'NO_EVIDENCE',
      answer: prepared ? '다음 신청 일정은 예시 응답에서 확인이 필요해요.' : '이 질문에 답할 수 있는 공고문 근거를 찾지 못했어요.',
      answerSource: prepared ? 'PREPARED_ANSWER' : null, evidence: [], helpLinks: [],
    }
  },
}

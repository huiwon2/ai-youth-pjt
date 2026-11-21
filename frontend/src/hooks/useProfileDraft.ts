import { useEffect, useState } from 'react'
import type { LocationSearch, Session } from '../types/contracts'
import { sessionKey } from '../api/firsthome'

function initialAnswers({ profile }: Session) {
  const choice = <T extends string>(value: T | null | undefined): T | '' => value ?? ''
  const bool = (value: boolean | null) => value === null ? '' : String(value)
  const amount = (value: number | null | undefined) => value == null ? '' : String(value / 10000)
  return {
    birth: String(profile.birthYear ?? ''), birthday: bool(profile.birthdayPassedThisYear), birthdayEdited: false,
    job: choice(profile.employmentStatus), owns: bool(profile.ownsHome), parent: choice(profile.parentAddressStatus),
    cash: amount(profile.availableCashWon), income: amount(profile.income?.amountWon), period: profile.income?.period ?? 'MONTHLY',
    commute: profile.commuteLimit?.type === 'LIMIT' ? String(profile.commuteLimit.minutes) : '',
    unlimited: profile.commuteLimit?.type === 'NO_LIMIT', move: choice(profile.moveIn?.type),
    query: '', needsPlace: false, place: null as LocationSearch['items'][number] | null, step: 0,
  }
}

export function useProfileDraft(session: Session) {
  const key = sessionKey + ':profile-draft:' + session.sessionId
  const [draft, setDraft] = useState(() => {
    const initial = initialAnswers(session)
    try {
      const stored = JSON.parse(sessionStorage.getItem(key) ?? 'null')
      if (stored?.revision !== session.revision || stored.date !== session.assessmentDate) return initial
      const answers = { needsPlace: false, ...stored.answers }
      if (!answers || !Number.isInteger(answers.step) || answers.step < 0 || answers.step > 10 ||
          Object.entries(initial).some(([field, value]) => typeof answers[field] !== typeof value) ||
          (answers.place !== null && typeof answers.place.locationId !== 'string')) return initial
      return { ...initial, ...answers } as typeof initial
    } catch {
      return initial
    }
  })
  useEffect(() => {
    sessionStorage.setItem(key, JSON.stringify({ answers: draft, revision: session.revision, date: session.assessmentDate }))
  }, [key, draft, session.revision, session.assessmentDate])
  function update(patch: Partial<typeof draft>) {
    setDraft(previous => ({ ...previous, ...patch }))
  }
  return { draft, update, clear: () => sessionStorage.removeItem(key) }
}

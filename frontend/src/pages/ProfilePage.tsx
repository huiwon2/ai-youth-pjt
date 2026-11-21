import { useState, type FormEvent, type ReactNode } from 'react'
import { api, demo } from '../api/firsthome'
import { errorMessage } from '../api/errors'
import { useProfileDraft } from '../hooks/useProfileDraft'
import { won } from '../utils/money'
import type { LocationSearch, ProfilePatch, Session } from '../types/contracts'

import { jobs, parents, moving } from '../utils/profileLabels'

const number = new Intl.NumberFormat('ko-KR')
const equal = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b)

function selected<T extends Record<string, string>>(options: T, value: string): keyof T | null {
  return Object.hasOwn(options, value) ? value : null
}

function Select({ label, value, options, onChange }: { label: string; value: string; options: Record<string, string>; onChange: (value: string) => void }) {
  return <select aria-label={label} value={value} onChange={event => onChange(event.target.value)}>
    <option value="">선택해 주세요</option>
    {Object.entries(options).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
  </select>
}

export default function ProfilePage({ session, easy, onSave, onError }: {
  session: Session; easy: boolean; onSave: (session: Session) => void; onError: (error: unknown) => void
}) {
  const profile = session.profile
  const { draft, update, clear } = useProfileDraft(session)
  const { birth, birthday, birthdayEdited, job, owns, parent, cash, income, period, commute, unlimited, move, query, place, step } = draft
  const [results, setResults] = useState<LocationSearch['items'] | null>(null)
  const [searching, setSearching] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [searchError, setSearchError] = useState('')

  const year = Number(session.assessmentDate.slice(0, 4))
  const birthYear = /^\d{4}$/.test(birth) && Number(birth) >= 1000 && Number(birth) <= year ? Number(birth) : null
  const cashWon = won(cash)
  const incomeWon = won(income)
  const destination = place ? { name: place.name, address: place.roadAddress ?? place.parcelAddress } : profile.frequentPlace
  const commuteLimit: ProfilePatch['commuteLimit'] = unlimited ? { type: 'NO_LIMIT' } :
    /^\d+$/.test(commute) && Number.isSafeInteger(Number(commute)) ? { type: 'LIMIT', minutes: Number(commute) } : null
  const moveIn = selected(moving, move)
  const birthdayValue = birthday === '' ? null : birthday === 'true'
  const age = birthYear === profile.birthYear && birthdayValue === profile.birthdayPassedThisYear ? profile.age : null

  async function search() {
    if (!query.trim() || searching) return
    setSearching(true)
    setSearchError('')
    setResults(null)
    try {
      const response = await api.search(session.sessionId, query.trim())
      setResults(response.items)
    } catch (reason) {
      setSearchError(errorMessage(reason))
      onError(reason)
    } finally {
      setSearching(false)
    }
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    if (count !== 9 || saving || (easy && !review)) return
    const values: ProfilePatch = {
      birthYear, employmentStatus: selected(jobs, job), ownsHome: owns === 'true',
      parentAddressStatus: selected(parents, parent), availableCashWon: cashWon,
      income: { period, amountWon: incomeWon! }, commuteLimit, moveIn: moveIn && { type: moveIn },
    }
    const patch: ProfilePatch = {}
    for (const key of Object.keys(values) as (keyof typeof values)[]) {
      const previous = key === 'moveIn' ? profile.moveIn && { type: profile.moveIn.type } : profile[key]
      if (!equal(previous, values[key])) Object.assign(patch, { [key]: values[key] })
    }
    if (birthdayEdited && (birthdayValue !== profile.birthdayPassedThisYear || birthYear !== profile.birthYear)) {
      patch.birthdayPassedThisYear = birthdayValue
    }
    if (place) patch.frequentPlace = { locationId: place.locationId }
    if (Object.keys(patch).length === 0 && session.progress.canEvaluate) {
      clear()
      onSave(session)
      return
    }
    setSaving(true)
    setError('')
    try {
      const result = await api.save(session.sessionId, session.revision, patch)
      clear()
      onSave(result)
      if (!result.progress.canEvaluate) setError('서버에서 확인이 필요한 항목이 있어요. 입력을 확인해 주세요.')
    } catch (reason) {
      setError(errorMessage(reason))
      onError(reason)
    } finally {
      setSaving(false)
    }
  }

  const fields: { label: string; summary: string; control: ReactNode }[] = [
    { label: '출생연도', summary: birthYear ? birth + '년' : '', control: <>
      <input aria-label="출생연도" inputMode="numeric" maxLength={4} placeholder="예: 1999" value={birth} onChange={event => {
        update({ birth: event.target.value, birthday: '', birthdayEdited: false })
      }} />
      {birth && birthYear === null && <small className="error">판정 기준 연도({year}년) 이하인 네 자리 연도를 입력해 주세요.</small>}
      {!easy && birthYear !== null && birthdayControl()}
      {age && <small>{age.exactYears === null ? '만 ' + age.minYears + '~' + age.maxYears + '세' : '만 ' + age.exactYears + '세'}</small>}
    </> },
    { label: '직업·학업 상태', summary: job ? jobs[job] : '', control: <Select label="직업·학업 상태" value={job} options={jobs} onChange={value => update({ job: selected(jobs, value) ?? '' })} /> },
    { label: '본인 명의 집', summary: owns ? owns === 'true' ? '있어요' : '없어요' : '', control: <Select label="본인 명의 집" value={owns} options={{ true: '있어요', false: '없어요' }} onChange={owns => update({ owns })} /> },
    { label: '부모님과 주민등록 주소가 같나요?', summary: parent ? parents[parent] : '', control: <Select label="부모님과 주민등록 주소가 같나요?" value={parent} options={parents} onChange={value => update({ parent: selected(parents, value) ?? '' })} /> },
    { label: '보증금으로 쓸 돈', summary: cashWon === null ? '' : number.format(cashWon / 10000) + '만 원', control: <>
      <div className="input-row"><input aria-label="보증금으로 쓸 돈 (만 원)" inputMode="decimal" placeholder="예: 2000" value={cash} onChange={event => update({ cash: event.target.value })} /><span>만 원</span></div>
      {cash && cashWon === null && <small className="error">0 이상의 금액을 만 원 단위로 입력해 주세요. 소수점은 4자리까지 입력할 수 있어요.</small>}
    </> },
    { label: '수입', summary: incomeWon === null ? '' : (period === 'MONTHLY' ? '월 ' : '연 ') + number.format(incomeWon / 10000) + '만 원', control: <>
      <div className="input-row">
        <select aria-label="수입 기준" value={period} onChange={event => update({ period: event.target.value === 'ANNUAL' ? 'ANNUAL' : 'MONTHLY' })}><option value="MONTHLY">월 소득</option><option value="ANNUAL">연 소득</option></select>
        <input aria-label="수입 (만 원)" inputMode="decimal" placeholder="예: 230" value={income} onChange={event => update({ income: event.target.value })} /><span>만 원</span>
      </div>
      {income && incomeWon === null && <small className="error">0 이상의 금액을 만 원 단위로 입력해 주세요. 소수점은 4자리까지 입력할 수 있어요.</small>}
    </> },
    { label: '자주 가는 곳', summary: destination?.name ?? '', control: <>
      <div className="input-row">
        <input aria-label="회사·학교·역 이름" value={query} disabled={searching} placeholder={demo ? '예: 멀티캠퍼스 역삼' : '회사·학교·역 이름'} onChange={event => { update({ query: event.target.value }); setResults(null); setSearchError('') }} onKeyDown={event => {
          if (event.key === 'Enter' && !event.nativeEvent.isComposing) { event.preventDefault(); void search() }
        }} />
        <button className="secondary" type="button" disabled={!query.trim() || searching} onClick={() => void search()}>{searching ? '검색 중…' : '검색'}</button>
      </div>
      {searchError && <small role="alert" className="error">{searchError}</small>}
      {results?.length === 0 && <small role="status">검색 결과가 없어요. 장소 이름이나 주소를 다시 확인해 주세요.</small>}
      {!!results?.length && <ul className="search-results">{results.map(item => <li key={item.locationId}>
        <button className="secondary" type="button" disabled={!item.selectable} onClick={() => { update({ place: item }); setResults(null) }}>
          {item.name}<small>{item.roadAddress ?? item.parcelAddress}{!item.selectable && ' · 선택할 수 없는 주소'}</small>
        </button>
      </li>)}</ul>}
      {destination && <small className="selected-place" role="status">선택한 곳 · {destination.name} {destination.address}</small>}
    </> },
    { label: '허용 이동 시간', summary: commuteLimit?.type === 'NO_LIMIT' ? '제한 없음' : commuteLimit ? commuteLimit.minutes + '분' : '', control: <>
      <div className="input-row"><input aria-label="허용 이동 시간 (분)" inputMode="numeric" disabled={unlimited} placeholder="예: 45" value={commute} onChange={event => update({ commute: event.target.value })} /><span>분</span></div>
      <label className="check"><input type="checkbox" checked={unlimited} onChange={event => update({ unlimited: event.target.checked })} /> 제한 없음</label>
      {commute && !commuteLimit && <small className="error">이동 시간을 0 이상의 정수로 입력해 주세요.</small>}
    </> },
    { label: '입주 시기', summary: move ? moving[move] : '', control: <Select label="입주 시기" value={move} options={moving} onChange={value => update({ move: selected(moving, value) ?? '' })} /> },
  ]
  const count = fields.filter(field => field.summary !== '').length
  const questions = ['몇 년에 태어났나요?', '지금 어떤 일을 하고 있나요?', '내 이름으로 된 집이 있나요?', '부모님과 주민등록 주소가 같나요?', '보증금으로 얼마를 쓸 수 있나요?', '수입은 얼마인가요?', '자주 가는 곳은 어디인가요?', '한 번 이동할 때 몇 분까지 괜찮나요?', '언제 이사하고 싶나요?']
  const wizard = [
    fields[0], { label: '올해 생일이 지났나요?', summary: '선택 항목', control: birthdayControl() }, ...fields.slice(1),
  ]
  const review = step === wizard.length
  const current = wizard[step]
  const questionLabel = step === 1 ? '올해 생일이 지났나요?' : questions[step > 1 ? step - 1 : step]

  function birthdayControl() {
    return <label className="nested">올해 생일
      <Select label="올해 생일" value={birthday} options={{ true: '오늘 또는 이미 지났어요', false: '아직 안 지났어요' }} onChange={value => { update({ birthday: value, birthdayEdited: true }) }} />
    </label>
  }

  function fieldCard(field: typeof fields[number]) {
    return <fieldset className="field" key={field.label}><legend aria-live={easy ? "polite" : undefined}>{field.label}</legend>{field.control}</fieldset>
  }

  return <>
    <div className="page-title"><h1>내 조건</h1><p>주거 지원을 확인할 수 있도록 아홉 가지 조건을 알려주세요.</p></div>
    <form className="profile-layout" onSubmit={save}>
      <div className="panel form-panel" key={easy ? 'easy' : 'standard'}>
        <fieldset className="form-fields" disabled={saving}>
          {easy ? review ? <h2>입력한 답변을 확인해 주세요</h2> : fieldCard({ ...current, label: questionLabel }) : <>
            <h2>기본 정보</h2><div className="field-grid">{fields.slice(0, 4).map(fieldCard)}</div>
            <h2>예산</h2><div className="field-grid">{fields.slice(4, 6).map(fieldCard)}</div>
            <h2>이사·이동 조건</h2><div className="field-grid">{fields.slice(6).map(fieldCard)}</div>
          </>}
        </fieldset>
        {easy && <div className="actions">
          {step > 0 && <button className="secondary" type="button" disabled={saving} onClick={() => update({ step: step - 1 })}>이전 질문</button>}
          {!review && <button type="button" disabled={!current.summary} onClick={() => update({ step: step + 1 })}>{step === 1 && !birthday ? '건너뛰기' : '다음'}</button>}
        </div>}
      </div>
      <aside className="panel summary">
        <h2>입력한 조건 <span className="count" aria-live="polite">{count}/9</span></h2>
        <progress value={count} max={9} aria-label="입력 진행도" />
        {(!easy || review) && <dl>{fields.map(field => <div key={field.label}><dt>{field.label}</dt><dd>{field.summary || '미입력'}</dd></div>)}</dl>}
        {(!easy || review) && birthday && <p className="muted">올해 생일 · {birthday === 'true' ? '오늘 또는 지났어요' : '아직 안 지났어요'}</p>}
        {(!easy || review) && <button className="full" disabled={count !== 9 || saving}>{saving ? '저장 중…' : '받을 수 있는 지원 보기'}</button>}
        {error && <p role="alert" className="error">{error}</p>}
      </aside>
    </form>
  </>
}

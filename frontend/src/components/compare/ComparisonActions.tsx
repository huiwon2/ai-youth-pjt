import { startTransition, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../../api/firsthome'
import { ApiError, errorMessage } from '../../api/errors'
import type { Comparison, ComparisonAnswer, PriorityPreset, Session } from '../../types/contracts'
import { money } from '../../utils/money'
import { slotName } from '../../utils/home'

const labels = { BALANCED: '균형', COST: '돈', COMMUTE: '시간', NEIGHBORHOOD: '동네', CUSTOM: '직접 설정' }
const presets: PriorityPreset[] = ['BALANCED', 'COST', 'COMMUTE', 'NEIGHBORHOOD']
const sampleQuestion = '성수와 역삼은 얼마나 차이 나?'
const fields: Record<string, string> = {
  'cost.monthlyHousingCostWon': '한 달 주거비', 'cost.maintenanceWon': '관리비',
  'cost.monthlyRentWon': '월세', 'cost.depositWon': '보증금', 'cost.depositShortfallWon': '보증금 부족분',
  'cost.monthlyAfterSupportWon': '지원 기간 중 한 달 주거비', 'cost.monthlyTransportFareWon': '월 교통비',
  'cost.excludedUnknownMaintenance': '미확인 관리비 제외', 'route.durationSeconds': '편도 이동시간',
  'route.oneWayFareWon': '편도 요금', 'scores.total': '종합 점수', rank: '순위',
}

function referenceText({ field, value }: ComparisonAnswer['references'][number]) {
  let formatted = String(value)
  if (value === null) formatted = '확인 필요'
  else if (typeof value === 'boolean') formatted = value ? '예' : '아니요'
  else if (typeof value === 'number' && field.endsWith('Won')) formatted = money(value)
  else if (typeof value === 'number' && field === 'route.durationSeconds') formatted = Math.ceil(value / 60) + '분'
  return (fields[field] ?? field) + ' · ' + formatted
}

export default function ComparisonActions({ session, result, comparisonId, mode, onSave, onRecalculated }: {
  session: Session; result: Comparison; comparisonId: string; mode: string; onSave: (session: Session) => void
  onRecalculated: (result: Comparison) => void
}) {
  const navigate = useNavigate()
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])
  const [busy, setBusy] = useState<'saving' | 'comparing' | 'asking' | null>(null)
  const [priorityError, setPriorityError] = useState('')
  const [question, setQuestion] = useState('')
  const [questionError, setQuestionError] = useState('')
  const [answer, setAnswer] = useState<ComparisonAnswer | null>(null)
  const [needsRefresh, setNeedsRefresh] = useState(false)
  const current = result.isCurrent && result.inputRevision === session.revision
  const savedPreset = session.preferences.priorityPreset

  async function recalculate(preset: PriorityPreset) {
    if (busy) return
    let saved = session
    let saving = preset !== savedPreset
    setPriorityError('')
    setQuestionError('')
    setBusy(saving ? 'saving' : 'comparing')
    try {
      if (saving) {
        saved = await api.savePriority(session.sessionId, session.revision, preset)
        if (!mounted.current) {
          onSave(saved)
          return
        }
        saving = false
        setBusy('comparing')
      }
      const next = await api.compare(saved.sessionId, saved.revision)
      if (!mounted.current) {
        onSave(saved)
        return
      }
      if (next.inputRevision !== saved.revision || !next.isCurrent || next.comparisonId === comparisonId) {
        throw new ApiError(409, '최신 입력으로 생성한 비교 결과를 확인할 수 없어요.')
      }
      startTransition(() => {
        onSave({ ...saved, latestComparisonId: next.comparisonId })
        onRecalculated(next)
        navigate('/compare/' + encodeURIComponent(next.comparisonId) + mode, { preventScrollReset: true })
      })
    } catch (reason) {
      if (saved !== session) onSave(saved)
      if (mounted.current) {
        setPriorityError((saving ? '우선순위 저장 실패 · ' : '비교 재계산 실패 · ') + errorMessage(reason))
        setNeedsRefresh(reason instanceof ApiError && [409, 412, 428].includes(reason.status))
      }
    } finally {
      if (mounted.current) setBusy(null)
    }
  }

  async function ask(text: string) {
    const trimmed = text.trim()
    if (busy || !current || !trimmed) return
    setQuestion(trimmed)
    setQuestionError('')
    setAnswer(null)
    setBusy('asking')
    try {
      const response = await api.askComparison(session.sessionId, comparisonId, result.inputRevision, trimmed)
      if (!mounted.current) return
      if (response.comparisonId !== comparisonId || response.inputRevision !== result.inputRevision) {
        throw new ApiError(409, '현재 보고 있는 비교와 답변의 기준이 달라요.')
      }
      setAnswer(response)
    } catch (reason) {
      if (mounted.current) {
        setQuestionError(errorMessage(reason))
        setNeedsRefresh(reason instanceof ApiError && reason.status === 409)
      }
    } finally {
      if (mounted.current) setBusy(null)
    }
  }

  return <>
    <section className="panel scenario">
      <h2>비교 우선순위</h2>
      <p>이 표의 기준은 <strong>{labels[result.priorityPreset]}</strong>이에요. 돈 {result.weights.cost}% · 시간 {result.weights.commute}% · 동네 {result.weights.convenience}%</p>
      <div className="actions" role="group" aria-label="우선순위 선택">
        {presets.map(preset => <button key={preset} type="button" className={preset === savedPreset ? '' : 'secondary'}
          aria-pressed={preset === savedPreset} disabled={!!busy || needsRefresh || (preset === savedPreset && current)}
          onClick={() => void recalculate(preset)}>{labels[preset]}{preset !== 'BALANCED' && ' 우선'}</button>)}
      </div>
      {busy === 'saving' && <p role="status">우선순위를 저장하고 있어요…</p>}
      {busy === 'comparing' && <p role="status">저장한 우선순위로 다시 비교하고 있어요…</p>}
      {priorityError && <p role="alert" className="error">{priorityError}</p>}
      {!current && !busy && !needsRefresh && <p>저장한 우선순위는 {labels[savedPreset]}예요. 우선순위를 선택하면 현재 조건으로 다시 비교해요.</p>}
    </section>
    <section className="panel scenario">
      <h2>이 비교에 대해 물어보기</h2>
      {current ? <><form onSubmit={event => { event.preventDefault(); void ask(question) }}>
        <div className="input-row">
          <input aria-label="비교 후속 질문" value={question} disabled={!!busy || needsRefresh} onChange={event => { setQuestion(event.target.value); setAnswer(null); setQuestionError('') }} />
          <button disabled={!!busy || needsRefresh || !question.trim()}>{busy === 'asking' ? '확인 중…' : '질문하기'}</button>
        </div>
      </form><button className="secondary" disabled={!!busy || needsRefresh} onClick={() => void ask(sampleQuestion)}>{sampleQuestion}</button></> :
        <p>현재 조건으로 다시 비교하면 새 결과에 질문할 수 있어요.</p>}
      {questionError && <p role="alert" className="error">{questionError}</p>}
      {current && answer && <div role="status" className="comparison-answer">
        <p>{answer.answer}</p>
        {answer.references.length > 0 && <><h3>답변 근거</h3><ul>{answer.references.map((reference, index) => {
          const home = result.homes.find(item => item.homeId === reference.homeId)
          return <li key={index}>{home ? '후보 ' + slotName(home.slot) + ' · ' + home.label : reference.homeId} · {referenceText(reference)}</li>
        })}</ul></>}
      </div>}
    </section>
    {needsRefresh && <button className="secondary" onClick={() => window.location.reload()}>최신 작업 다시 불러오기</button>}
  </>
}

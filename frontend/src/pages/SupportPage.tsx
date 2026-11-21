import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, demo } from '../api/firsthome'
import { errorMessage } from '../api/errors'
import { readSavedPolicies, writeSavedPolicies, type SavedPolicy } from '../stores/savedPolicies'
import EasySupport from '../components/easy/EasySupport'
import { money } from '../utils/money'
import type { Policy, PolicyAnswer, PolicyEvaluation, Session } from '../types/contracts'

const eligibility = { MET: '조건 충족', NOT_MET: '조건 불충족', CHECK_NEEDED: '조건 확인 필요' }
const applications = { OPEN: '접수 중', ALWAYS: '상시 접수', UPCOMING: '접수 예정', CLOSED: '접수 마감', UNKNOWN: '접수 확인 필요' }
function Sources({ evidence }: { evidence: PolicyEvaluation['evidence'] }) {
  return evidence.length > 0 && <ul>{evidence.map(item => <li key={item.id}>
    <strong>{item.title}</strong><p>{item.excerpt}</p>{item.sourceUrl && <a href={item.sourceUrl}>공고문 원문</a>}
  </li>)}</ul>
}

function PolicyCard({ policy, included, saved, onToggle }: { policy: Policy; included: boolean; saved: boolean; onToggle: () => void }) {
  const benefit = policy.benefit
  return <article className="panel policy">
    <h3>{policy.name}</h3>
    <div className="tags">
      <span className={'tag ' + policy.eligibilityStatus.toLowerCase()}>{eligibility[policy.eligibilityStatus]}</span>
      <span className="tag">{applications[policy.applicationStatus]}</span>
    </div>
    <p className="policy-amount">{money(benefit.potentialTotalWon)} {demo && <small>예시</small>}</p>
    {benefit.monthlyWon !== null && <p>월 {money(benefit.monthlyWon)} · {benefit.durationMonths === null ? '기간 확인 필요' : benefit.durationMonths + '개월'}</p>}
    {benefit.oneTimeWon !== null && <p>일회성 {money(benefit.oneTimeWon)}</p>}
    {included && <small>예상 지원 금액에 자동 반영</small>}
    <div className="actions"><button type="button" className="secondary" aria-pressed={saved} onClick={onToggle}>{saved ? '담기 해제' : '지원 담기'}</button></div>
    <details>
      <summary>상세 조건과 신청 방법</summary>
      {policy.conditions.length === 0 ? <p>세부 조건은 공고문에서 확인해 주세요.</p> :
        <ul>{policy.conditions.map(condition => <li key={condition.key}>
          <strong>{condition.label} · {eligibility[condition.status]}</strong>
          {condition.actual && <p>내 조건 · {condition.actual}</p>}
          {condition.expected && <p>공고 기준 · {condition.expected}</p>}
          <p>{condition.reason}</p>
        </li>)}</ul>}
      {policy.methodText && <p>{policy.methodText}</p>}
      {policy.rounds.map(round => <p key={round.roundId}>{applications[round.status]} · {round.startDate ?? '시작일 확인 필요'} ~ {round.endDate ?? '종료일 확인 필요'}
        {round.sourceUrl && <> · <a href={round.sourceUrl}>회차 공고문</a></>}
      </p>)}
      {policy.applicationStatus === 'UPCOMING' && !policy.rounds.some(round => round.startDate) && <p>다음 모집 일정 확인 필요</p>}
      {policy.sourceUrl && <a href={policy.sourceUrl}>공고문 원문</a>}
      <Sources evidence={policy.evidence} />
    </details>
  </article>
}

export default function SupportPage({ session, easy, onError }: {
  session: Session; easy: boolean; onError: (reason: unknown) => void
}) {
  const { sessionId, revision } = session
  const [result, setResult] = useState<PolicyEvaluation | null>(null)
  const [saved, setSaved] = useState(() => readSavedPolicies(sessionId))
  const [saveError, setSaveError] = useState('')
  function storeSelection(next: SavedPolicy[]) {
    try {
      writeSavedPolicies(sessionId, next)
      setSaved(next)
      setSaveError('')
    } catch {
      setSaveError('선택을 저장할 수 없어요. 브라우저 저장 공간을 확인해 주세요.')
    }
  }
  function remove(key: string) {
    storeSelection(saved.filter(item => item.policyKey !== key))
  }
  const [failure, setFailure] = useState<{ message: string; reason: unknown } | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [scenario, setScenario] = useState<PolicyEvaluation | null>(null)
  const [scenarioOpen, setScenarioOpen] = useState(false)
  const [scenarioPending, setScenarioPending] = useState(false)
  const [scenarioError, setScenarioError] = useState('')
  const [question, setQuestion] = useState('')
  const [answer, setAnswer] = useState<PolicyAnswer | null>(null)
  const [asking, setAsking] = useState(false)
  const [questionError, setQuestionError] = useState('')

  useEffect(() => {
    let active = true
    api.evaluate(sessionId, revision).then(value => {
      if (!active) return
      if (value.inputRevision === revision) setResult(value)
      else throw new Error('입력 버전이 달라졌어요.')
    }).catch(reason => {
      if (active) setFailure({ message: errorMessage(reason), reason })
    })
    return () => { active = false }
  }, [sessionId, revision, attempt])

  async function openScenario() {
    setScenarioOpen(true)
    if (scenario || scenarioPending) return
    setScenarioPending(true)
    setScenarioError('')
    try {
      const value = await api.scenario(sessionId, revision)
      if (value.inputRevision === revision) setScenario(value)
      else setScenarioError('내 조건이 바뀌었어요. 다시 확인해 주세요.')
    } catch (reason) {
      setScenarioError(errorMessage(reason))
    } finally {
      setScenarioPending(false)
    }
  }

  if (failure) return <section className="panel">
    <h1>받을 지원</h1><p role="alert" className="error">{failure.message}</p>
    <button onClick={() => {
      setFailure(null)
      setAttempt(value => value + 1)
      onError(failure.reason)
    }}>다시 불러오기</button>
  </section>

  if (!result) return <section className="panel"><h1>받을 지원</h1><p role="status">내 조건으로 지원 결과를 확인하고 있어요…</p></section>

  const { summary, policies } = result
  const mode = easy ? '?mode=easy' : ''
  const selection = <section className="panel scenario"><h2>직접 담은 지원 · {saved.length}개</h2>
    {saved.length === 0 ? <p>다시 보고 싶은 정책에서 ‘지원 담기’를 눌러 주세요.</p> :
      <ul>{saved.map(item => <li key={item.policyKey}>{item.name} · 담을 때 예상 금액 {money(item.amountWon)} {demo && '(시연 예시)'}{' '}
        <button type="button" className="secondary" aria-label={item.name + ' 담기 해제'} onClick={() => remove(item.policyKey)}>해제</button>
      </li>)}</ul>}
    {saveError && <p role="alert" className="error">{saveError}</p>}
  </section>
  const details = <>
    {scenarioOpen && <section className="panel scenario" aria-labelledby="scenario-title">
      <h2 id="scenario-title">만 35세일 때 예상 지원</h2>
      {scenarioPending && <p role="status">가정 조건을 확인하고 있어요…</p>}
      {scenarioError && <p role="alert" className="error">{scenarioError}</p>}
      {scenario?.delta && <p>{money(scenario.delta.baseTotalWon)} → <strong>{money(scenario.delta.scenarioTotalWon)}</strong> {demo && '(예시)'}</p>}
      {scenario?.delta?.removedPolicyKeys.map(key => <p key={key}>{policies.find(policy => policy.policyKey === key)?.name ?? key} · 예상 금액에서 제외</p>)}
      <button className="secondary" onClick={() => setScenarioOpen(false)}>닫기</button>
    </section>}
    <h2 className="section-title">정책별 판정</h2>
    <div className="policies">{policies.map(policy => {
      const selected = saved.some(item => item.policyKey === policy.policyKey)
      return <PolicyCard key={policy.policyKey} policy={policy} included={summary.includedPolicyKeys.includes(policy.policyKey)} saved={selected}
        onToggle={() => selected ? remove(policy.policyKey) : storeSelection([...saved, {
          policyKey: policy.policyKey, name: policy.name, amountWon: policy.benefit.potentialTotalWon, assessmentDate: result.assessmentDate,
        }])} />
    })}</div>
    {policies.length === 0 && <p>현재 조건에서 확인된 지원이 없어요.</p>}
    {result.conflicts.length > 0 && <section className="panel scenario"><h2>중복 수혜 안내</h2><ul>
      {result.conflicts.map(conflict => <li key={conflict.includedPolicyKey + ':' + conflict.excludedPolicyKey}>{conflict.reason}</li>)}
    </ul><Sources evidence={result.evidence} /></section>}
    {(result.calendar.length > 0 || result.checklist.length > 0) && <section className="panel scenario">
      <h2>신청 일정과 할 일</h2>
      <ul>{[...result.calendar, ...result.checklist].map((item, index) => <li key={index}>
        {item.title}{item.startDate && ' · ' + item.startDate}{item.endDate && ' ~ ' + item.endDate}
      </li>)}</ul>
    </section>}
    <section className="panel scenario">
      <h2>공고문에 물어보기</h2>
      <form onSubmit={async event => {
        event.preventDefault()
        if (asking || !question.trim()) return
        setAsking(true)
        setAnswer(null)
        setQuestionError('')
        try {
          const value = await api.ask(sessionId, revision, question.trim())
          if (value.inputRevision === revision) setAnswer(value)
          else setQuestionError('내 조건을 다시 불러와 주세요.')
        } catch (reason) {
          setQuestionError(errorMessage(reason))
        } finally {
          setAsking(false)
        }
      }}>
        <div className="input-row">
          <input aria-label="공고문 질문" placeholder="예: 언제 신청할 수 있나요?" value={question} disabled={asking} onChange={event => setQuestion(event.target.value)} />
          <button disabled={asking || !question.trim()}>{asking ? '확인 중…' : '질문하기'}</button>
        </div>
      </form>
      {questionError && <p role="alert" className="error">{questionError}</p>}
      {answer && <div role="status">
        {answer.answerSource === 'PREPARED_ANSWER' && <small>준비된 시연 답변</small>}
        {answer.status === 'NO_EVIDENCE' && <small>공고문 근거 없음</small>}
        <p>{answer.answer}</p><Sources evidence={answer.evidence} />
        {answer.helpLinks.map(link => <p key={link.url}><a href={link.url}>{link.label}</a></p>)}
      </div>}
    </section>
  </>
  if (easy) return <>
    <EasySupport result={result} />
    {selection}
    <details className="panel easy-details"><summary>정책과 공고문 자세히 보기</summary>
      <button className="secondary" onClick={() => void openScenario()}>35살이 되면?</button>
      {details}
    </details>
  </>
  return <>
    <div className="page-title"><h1>받을 지원</h1><p>내 조건과 공고 기준을 비교한 결과예요.</p></div>
    <section className="result-banner">
      <p>{summary.targetYear}년 예상 지원 금액 {demo && <span className="tag">시연 예시</span>}</p>
      <strong>{money(summary.potentialTotalWon)}</strong>
      <p>{summary.totalStatus === 'PARTIAL' ? '확인된 금액만 반영했어요.' : summary.totalStatus === 'UNKNOWN' ? '공고에서 금액을 확인해 주세요.' : '지원별 세부 조건과 접수 상태를 확인해 주세요.'}</p>
      <small>판정 기준일 · {result.assessmentDate}</small>
    </section>
    <div className="actions">
      <Link className="button" to={'/compare' + mode}>집 고르기</Link>
      <Link className="button secondary" to={'/profile' + mode}>내 조건 고치기</Link>
      <button type="button" className="secondary" onClick={() => void openScenario()}>35살이 되면?</button>
    </div>
    {selection}
    {details}
  </>
}

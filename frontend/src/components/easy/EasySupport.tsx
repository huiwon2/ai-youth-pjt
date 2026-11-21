import { Link } from 'react-router'
import type { PolicyEvaluation } from '../../types/contracts'
import { money } from '../../utils/money'

export default function EasySupport({ result }: { result: PolicyEvaluation }) {
  const { summary, policies, checklist } = result
  const empty = policies.length === 0
  const noneEligible = !empty && policies.every(policy => policy.eligibilityStatus === 'NOT_MET')
  const tasks = checklist.length ? checklist : result.calendar
  const pending = policies.filter(policy => policy.eligibilityStatus === 'CHECK_NEEDED')
  const available = policies.filter(policy => policy.eligibilityStatus === 'MET')
  return <>
    <div className="page-title"><h1>받을 수 있는 돈</h1></div>
    <section className="result-banner">
      <p>{summary.targetYear}년 예상 지원 금액</p>
      <strong>{money(summary.potentialTotalWon)}</strong>
      {summary.totalStatus === 'PARTIAL' && <p>금액을 확인한 지원만 더했어요.</p>}
      {summary.potentialTotalWon === null && <p>지원 금액을 확인할 정보가 더 필요해요.</p>}
    </section>
    {empty && <p>아직 조회할 수 있는 정책 정보가 없어요.</p>}
    {noneEligible && <section className="panel scenario"><h2>현재 조건에 맞는 지원이 없어요</h2>
      <ul>{policies.map(policy =>
        <li key={policy.policyKey}>{policy.name} · {policy.conditions.filter(condition => condition.status === 'NOT_MET').map(condition => condition.reason || condition.label).join(' / ') || '공고의 신청 조건과 맞지 않아요.'}</li>
      )}</ul>
    </section>}
    <section className="panel scenario"><h2>지금 할 일</h2>
      {tasks.length > 0 ? <ul>{tasks.map((task, index) => <li key={index}>{task.title} {task.startDate}{task.endDate && ' ~ ' + task.endDate}</li>)}</ul> :
        empty || noneEligible ? <p>입력한 조건을 다시 살펴보거나 비교할 집을 넣어 보세요.</p> : <>
          {available.map(policy => <p key={policy.policyKey}><strong>{policy.name}</strong> · {policy.applicationStatus === 'OPEN' || policy.applicationStatus === 'ALWAYS' ? policy.methodText || '공고에서 신청 방법을 확인해 보세요.' : policy.applicationStatus === 'CLOSED' ? '접수가 끝났어요. 다음 모집 일정을 확인해 보세요.' : '다음 모집 일정을 확인해 보세요.'}</p>)}
          {pending.map(policy => <p key={policy.policyKey}><strong>{policy.name}</strong> · {policy.conditions.find(condition => condition.status === 'CHECK_NEEDED')?.reason || '공고에서 신청 조건을 확인해 보세요.'}</p>)}
        </>}
    </section>
    <div className="actions"><Link className="button" to="/compare?mode=easy">집 고르기</Link>
      <Link className="button secondary" to="/profile?mode=easy">내 조건 고치기</Link></div>
  </>
}

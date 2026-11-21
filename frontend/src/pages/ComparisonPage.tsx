import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/firsthome'
import { errorMessage } from '../api/errors'
import type { Comparison, Session } from '../types/contracts'
import ComparisonMap from '../components/map/ComparisonMap'
import { homeTitle } from '../utils/home'
import EasyComparison from '../components/easy/EasyComparison'
import ComparisonActions from '../components/compare/ComparisonActions'
import { comparisonRows } from '../utils/comparisonRows'
import ExportComparison from '../components/result/ExportComparison'

export default function ComparisonPage({ session, comparisonId, mode, onSave, initialResult, onRecalculated }: {
  session: Session; comparisonId: string; mode: string; onSave: (session: Session) => void
  initialResult: Comparison | null; onRecalculated: (result: Comparison) => void
}) {
  const [result, setResult] = useState(initialResult)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let active = true
    api.comparison(session.sessionId, comparisonId).then(value => {
      if (!active) return
      if (value.comparisonId !== comparisonId) setError('요청한 비교 결과를 확인할 수 없어요.')
      else setResult(value)
    }).catch(reason => { if (active) setError(errorMessage(reason)) })
    return () => { active = false }
  }, [comparisonId, session.sessionId, attempt])
  const winner = result?.homes.find(home => home.homeId === result.recommendation.homeId)
  return <div className="comparison-result">
    <div className="page-title"><h1>후보 집 비교 결과</h1><p><Link to={'/compare' + mode}>후보 목록으로</Link></p></div>
    {error ? <div role="alert">{error} <button onClick={() => { setError(''); setAttempt(value => value + 1) }}>다시 조회</button></div> : !result ? <p role="status">저장한 결과를 불러오는 중…</p> : <>
      {(!result.isCurrent || result.inputRevision !== session.revision) && <p className="panel">저장된 이전 결과예요. 후보 목록에서 현재 조건으로 다시 비교할 수 있어요.</p>}
      {mode ? <EasyComparison result={result} /> : <>
      <section className="result-banner"><p>비교 기준일 · {result.assessmentDate}</p>
        <h2>{winner ? '추천 후보 · ' + winner.label : '추천 순위 보류'}</h2>
        {[...result.recommendation.reasons, ...result.recommendation.tradeoffs].map(line => <p key={line}>{line}</p>)}
        {result.homes.every(home => home.cost.maintenanceWon === null) && <p>각 집의 관리비를 확인해 주세요.</p>}
      </section>
      <div className="panel comparison-table" tabIndex={0} role="region" aria-label="후보별 비교표">
        <table><caption>후보별 비용과 이동 조건</caption>
          <thead><tr><th scope="col">비교 항목</th>{result.homes.map(home => <th scope="col" key={home.homeId}>{homeTitle(home)}<small>{home.location.address}</small></th>)}</tr></thead>
          <tbody>{comparisonRows.map(([name, format]) => <tr key={name}><th scope="row">{name}</th>{result.homes.map(home => <td key={home.homeId}>{format(home)}</td>)}</tr>)}</tbody>
        </table>
      </div>
      </>}
      <ComparisonMap result={result} destination={result.destination === undefined
        ? (result.isCurrent && result.inputRevision === session.revision ? session.profile.frequentPlace : null)
        : result.destination} />
      <ExportComparison result={result} session={session} />
      <ComparisonActions session={session} result={result} comparisonId={comparisonId} mode={mode} onSave={onSave} onRecalculated={onRecalculated} />
      {!mode && result.warnings.map((item, index) => <p key={index}>{item.message}</p>)}
    </>}
  </div>
}

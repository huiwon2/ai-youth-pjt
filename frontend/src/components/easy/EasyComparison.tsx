import { Link } from 'react-router'
import { demo } from '../../api/firsthome'
import type { Comparison } from '../../types/contracts'
import { money } from '../../utils/money'
import { slotName, travel } from '../../utils/home'

export default function EasyComparison({ result }: { result: Comparison }) {
  const allUnknown = result.homes.length > 0 && result.homes.every(home => home.cost.maintenanceWon === null)
  const winner = result.homes.find(home => home.homeId === result.recommendation.homeId)
  return <>
    <section className="result-banner">
      {demo && <p>시연 예시</p>}
      <h2>{allUnknown ? '먼저 관리비를 확인해 주세요' : winner ? winner.label + '부터 살펴보세요' : '먼저 볼 집을 아직 정하기 어려워요'}</h2>
      {allUnknown ? <>
        <p>모든 후보의 관리비가 비어 있어요. 한 달에 얼마를 내는지 알려면 집마다 관리비가 얼마인지 물어보세요.</p>
        <p>관리비가 없는 집은 0원을 입력해 주세요.</p>
      </> : <>
        {result.recommendation.reasons.map(line => <p key={line}>{line}</p>)}
        {result.recommendation.tradeoffs.map(line => <p key={line}>{line}</p>)}
        {!winner && result.warnings.map((item, index) => <p key={index}>{item.message}</p>)}
      </>}
    </section>
    <div className="easy-homes">{result.homes.map(home => <section className="panel" key={home.homeId}>
      <h3>후보 {slotName(home.slot)} · {home.label}</h3>
      <dl>
        <div><dt>한 달 주거비</dt><dd>{money(home.cost.monthlyHousingCostWon)}{home.cost.excludedUnknownMaintenance && ' + 관리비 별도'}</dd></div>
        <div><dt>관리비</dt><dd>{home.cost.maintenanceWon === null ? '미확인' : money(home.cost.maintenanceWon)}</dd></div>
        <div><dt>편도 이동시간</dt><dd>{travel(home.route)}</dd></div>
      </dl>
    </section>)}</div>
    {allUnknown && <Link className="button" to="/compare?mode=easy">후보 목록에서 관리비 입력하기</Link>}
  </>
}

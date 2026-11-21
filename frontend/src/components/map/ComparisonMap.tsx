import { useMemo, useState } from 'react'
import type { Comparison, Profile } from '../../types/contracts'
import { facilityText, homeTitle, travel } from '../../utils/home'
import MapCanvas from './MapCanvas'
import { mapData } from './mapData'

export default function ComparisonMap({ result, destination }: { result: Comparison; destination: Profile['frequentPlace'] }) {
  const demo = result.calculationVersion.startsWith('demo:')
  const [open, setOpen] = useState(false)
  const [id, select] = useState(result.homes[0]?.homeId)
  const home = result.homes.find(item => item.homeId === id) ?? result.homes[0]
  const data = useMemo(() => home && mapData(result, destination, home), [result, destination, home])
  if (!home || !data) return null
  const route = home.route
  return <section className="panel scenario">
    <h2>후보 집 지도</h2>
    <button className="secondary" type="button" aria-expanded={open} onClick={() => setOpen(!open)}>{open ? '지도 닫기' : '지도 열기'}</button>
    {open && <>
      <p>기준 목적지 · {destination?.name ?? '비교 당시 목적지 미확인'} {demo && '(시연 예시)'}</p>
      <div className="actions" role="group" aria-label="지도에서 볼 후보">{result.homes.map(item => <button
        className={home.homeId === item.homeId ? '' : 'secondary'} type="button" key={item.homeId}
        aria-pressed={home.homeId === item.homeId} onClick={() => select(item.homeId)}>{homeTitle(item)}</button>)}</div>
      <MapCanvas data={data} selected={home.homeId} onSelect={select} />
      <div className="map-detail" aria-live="polite">
        <h3>{homeTitle(home)}</h3><p>{home.location.address}</p>
        <p>편도 이동 · {travel(route)}{route.distanceMeters !== null && ' · ' + route.distanceMeters.toLocaleString('ko-KR') + 'm'}</p>
        <p>{route.status === 'ESTIMATED' ? '거리 기반 추정값이에요.' : route.status === 'OK' ? (route.source === 'DEMO' ? '시연 경로예요.' : '조회한 경로예요.') :
          route.status === 'NOT_FOUND' ? '이 목적지까지의 경로를 찾지 못했어요.' : '이동 경로 조회에 실패했어요.'}
          {' '}{data.path.length ? (data.estimated ? '점선은 추정 경로예요.' : '실선으로 경로를 표시했어요.') : '표시할 경로 좌표가 없어요.'}</p>
        <h4>주변 시설</h4>{home.facilities.length ? <ul>{home.facilities.map((item, index) =>
          <li key={index}>{facilityText(item)}{item.status === 'FOUND' && !item.location && ' · 위치 미확인'}</li>)}</ul> : <p>확인된 주변 시설 정보가 없어요.</p>}
      </div>
    </>}
  </section>
}

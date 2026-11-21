import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, demo } from '../api/firsthome'
import { ApiError, errorMessage } from '../api/errors'
import type { Home, HomePreview, LocationSearch, Session } from '../types/contracts'
import { money, won } from '../utils/money'
import { facilityText, slotName, slots, travel } from '../utils/home'

type Props = { session: Session; mode: string; onSave: (session: Session) => void; onError: (reason: unknown) => void }

export default function ComparePage({ session, mode, onSave, onError }: Props) {
  const navigate = useNavigate()
  const [slot, setSlot] = useState<Home['slot'] | null>(null)
  const [query, setQuery] = useState('')
  const [items, setItems] = useState<LocationSearch['items'] | null>(null)
  const [preview, setPreview] = useState<HomePreview | null>(null)
  const [deposit, setDeposit] = useState('')
  const [rent, setRent] = useState('')
  const [fee, setFee] = useState('')
  const [label, setLabel] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const active = useRef(true)
  useEffect(() => {
    active.current = true
    return () => { active.current = false }
  }, [])
  const { sessionId, revision, homes } = session

  function edit(next: typeof slot) {
    setSlot(next)
    setQuery('')
    setItems(null)
    setPreview(null)
    setDeposit('')
    setRent('')
    setFee('')
    setLabel('')
    setError('')
  }

  async function run(action: () => Promise<void>) {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      await action()
    } catch (reason) {
      if (!active.current) return
      setError(errorMessage(reason))
      if (reason instanceof ApiError && reason.code === 'PREVIEW_EXPIRED') setPreview(null)
      else onError(reason)
    } finally {
      if (active.current) setBusy(false)
    }
  }

  const depositWon = won(deposit)
  const monthlyRentWon = won(rent)
  const maintenanceWon = won(fee)
  const valid = depositWon !== null && monthlyRentWon !== null && (!fee || maintenanceWon !== null)

  const easy = mode === '?mode=easy'
  const nextSlot = slots.find(position => !homes.some(home => home.slot === position))
  const visibleSlots = easy ? slots.filter(position => homes.some(home => home.slot === position) || position === nextSlot) : slots

  return <>
    <div className="page-title"><h1>{easy ? '비교할 집을 넣어 주세요' : '집 고르기'}</h1><p>후보 집 2~3곳의 주거비와 {session.profile.frequentPlace?.name}까지의 이동시간을 비교해 보세요.</p></div>
    {easy && <p className="easy-instruction">{slot ? '주소를 검색하고 집을 고른 뒤 보증금과 월세를 적어 주세요.' : homes.length < 2 ? '비교하려면 집이 두 곳 필요해요. 후보 추가를 눌러 시작하세요.' : '아래의 비교하기를 눌러 어느 집이 맞을지 살펴보세요.'}</p>}
    <div className="candidate-grid" hidden={easy && slot !== null}>{visibleSlots.map(position => {
      const home = homes.find(item => item.slot === position)
      return <section className="panel candidate" key={position}>
        <small>후보 {slotName(position)}</small><h2>{home?.label ?? '비어 있는 후보'}</h2>
        {home && <><p>{home.location.address}</p><dl>
          <div><dt>보증금</dt><dd>{money(home.depositWon)}</dd></div>
          <div><dt>월세</dt><dd>{money(home.monthlyRentWon)}</dd></div>
          <div><dt>관리비</dt><dd>{home.maintenanceWon === null ? '미확인' : money(home.maintenanceWon)}</dd></div>
        </dl></>}
        <div className="actions"><button className="secondary" disabled={busy || slot !== null} onClick={() => edit(position)}>후보 {slotName(position)} {home ? '교체' : '추가'}</button>
        {home && <button className="secondary" disabled={busy || slot !== null} onClick={() => void run(async () => {
          onSave(await api.deleteHome(sessionId, revision, position))
        })}>후보 {slotName(position)} 삭제</button>}</div>
      </section>
    })}</div>
    {slot !== null && <section className="panel scenario"><h2>후보 {slotName(slot)} 주소와 계약 조건</h2>
      <form onSubmit={event => {
        event.preventDefault()
        if (query.trim()) void run(async () => {
          setItems(null)
          setPreview(null)
          setItems((await api.search(sessionId, query.trim(), 'HOME')).items)
        })
      }}>
        <label>집 주소<input value={query} disabled={busy} placeholder={demo ? '성수동, 역삼동 또는 신림동' : '건물 번호가 포함된 주소'} onChange={event => {
          setQuery(event.target.value); setItems(null); setPreview(null)
        }} /></label><button className="secondary" disabled={busy || !query.trim()}>주소 검색</button>
      </form>
      {items?.length === 0 && <p role="status">검색 결과가 없어요. 주소를 다시 확인해 주세요.</p>}
      {!preview && items?.map(item => <div key={item.locationId}><button className="secondary" disabled={busy || !item.selectable} onClick={() => void run(async () => {
        const value = await api.previewHome(sessionId, revision, item.locationId)
        setPreview(value); setLabel(value.location.name)
      })}>{item.name} · {item.roadAddress ?? item.parcelAddress}{!item.selectable && ' · 건물 주소 확인 필요'}</button></div>)}
      {preview && <>
        <h3>{preview.location.name}</h3><p>{preview.location.address}</p>
        <p>{session.profile.frequentPlace?.name}까지 편도 · {travel(preview.route)} {demo && '(시연 예시)'}</p>
        <h4>주변 시설</h4>{preview.facilities.length ? <ul>{preview.facilities.map(item => <li key={item.category}>{facilityText(item)}</li>)}</ul> : <p>확인된 시설 정보가 없어요.</p>}
        {preview.warnings.map(item => <p key={item.code}>{item.message}</p>)}
        <form onSubmit={event => {
          event.preventDefault()
          if (depositWon === null || monthlyRentWon === null || !valid) return
          void run(async () => {
            const saved = await api.putHome(sessionId, revision, slot, {
              previewId: preview.previewId, label: label.trim() || undefined, depositWon, monthlyRentWon, maintenanceWon, inputMethod: 'MANUAL',
            })
            onSave(saved)
            edit(null)
          })
        }}>
          <fieldset className="field-grid form-fields" disabled={busy}>
            <label>집 이름<input value={label} onChange={event => setLabel(event.target.value)} /></label>
            <label>보증금 (만 원)<input inputMode="decimal" value={deposit} onChange={event => setDeposit(event.target.value)} /></label>
            <label>월세 (만 원)<input inputMode="decimal" value={rent} onChange={event => setRent(event.target.value)} /></label>
            <label>관리비 (만 원)<input inputMode="decimal" placeholder="미확인이면 비워 두세요" value={fee} onChange={event => setFee(event.target.value)} /></label>
          </fieldset>
          {easy && <p>관리비를 모르면 비워 두세요. 관리비가 없다고 확인했으면 0을 입력하세요.</p>}
          <button disabled={busy || !valid}>후보 {slotName(slot)} 저장</button>
        </form>
      </>}
      <button className="secondary" disabled={busy} onClick={() => edit(null)}>취소</button>
    </section>}
    {error && <p role="alert" className="error">{error}</p>}
    <div className="actions"><button disabled={busy || slot !== null || homes.length < 2 || homes.length > 3 || !session.progress.canCompare} onClick={() => void run(async () => {
      const result = await api.compare(sessionId, revision)
      if (!active.current) return
      if (result.inputRevision !== revision) throw new ApiError(409, '입력이 변경되어 조건을 다시 불러옵니다.')
      onSave({ ...session, latestComparisonId: result.comparisonId })
      navigate('/compare/' + encodeURIComponent(result.comparisonId) + mode)
    })}>비교하기</button><span>{homes.length}/3곳 저장</span>
    {session.latestComparisonId && <Link to={'/compare/' + encodeURIComponent(session.latestComparisonId) + mode}>최근 비교 결과</Link>}</div>
  </>
}

import { useMemo, useState } from 'react'
import type { Home, HomePreview, Profile } from '../../types/contracts'
import { slotName } from '../../utils/home'
import MapCanvas from './MapCanvas'
import { routePath, validPoint, type Pin } from './mapData'

type Props = { homes: Home[]; destination: Profile['frequentPlace']; slot: Home['slot'] | null; preview: HomePreview | null }

export default function CandidateMap({ homes, destination, slot, preview }: Props) {
  const [selected, select] = useState('')
  const data = useMemo(() => {
    const pins: Pin[] = homes.filter(home => !preview || home.slot !== slot).map(home => ({
      kind: 'home', id: home.homeId, point: [home.location.longitude, home.location.latitude],
      badge: '후보 ' + slotName(home.slot), title: home.label + ' · ' + home.location.address,
    }))
    if (slot && preview) pins.push({ kind: 'home', id: preview.previewId, badge: '후보 ' + slotName(slot),
      point: [preview.location.longitude, preview.location.latitude], title: preview.location.address })
    if (destination) pins.push({ kind: 'destination', id: 'destination', badge: '목적지',
      point: [destination.longitude, destination.latitude], title: destination.name })
    return { pins: pins.filter(pin => validPoint(pin.point)), path: preview && (!pins.some(pin => pin.id === selected) || selected === preview.previewId) ? routePath(preview.route) : [],
      estimated: preview?.route.status === 'ESTIMATED' }
  }, [homes, destination, slot, preview, selected])
  const pin = data.pins.find(pin => pin.id === selected) ?? data.pins.find(pin => pin.id === preview?.previewId) ?? data.pins[0]
  return <section className="panel candidate-map"><h2>후보 집 위치</h2>
    <MapCanvas data={data} selected={pin?.id ?? ''} onSelect={select} />
    {pin && <p aria-live="polite">{pin.badge} · {pin.title}</p>}
  </section>
}

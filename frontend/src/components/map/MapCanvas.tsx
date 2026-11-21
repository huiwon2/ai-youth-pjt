import { useEffect, useRef, useState } from 'react'
import { loadMaps, type KakaoMap, type Maps } from '../../lib/kakao/sdk'
import LocationDiagram from './LocationDiagram'
import type { MapData } from './mapData'
import './map.css'

const sdkKey = import.meta.env.VITE_KAKAO_JS_KEY?.trim()

function drawLayers(sdk: Maps, map: KakaoMap, data: MapData, selected: string, onSelect: (id: string) => void) {
  const layers: { setMap(map: null): void }[] = []
  const bounds = new sdk.LatLngBounds()
  const clear = () => layers.forEach(layer => layer.setMap(null))
  try {
    for (const pin of data.pins) {
      const position = new sdk.LatLng(pin.point[1], pin.point[0])
      bounds.extend(position)
      const el = document.createElement(pin.kind === 'home' ? 'button' : 'span')
      el.textContent = pin.badge
      el.className = 'map-pin ' + pin.kind
      el.title = pin.title
      el.setAttribute('aria-label', pin.title)
      if (el instanceof HTMLButtonElement) {
        el.type = 'button'
        el.setAttribute('aria-pressed', String(pin.id === selected))
        el.onclick = () => onSelect(pin.id)
      }
      layers.push(new sdk.CustomOverlay({ map, position, content: el, clickable: true, yAnchor: pin.kind === 'destination' ? 0 : 1, zIndex: pin.id === selected ? 4 : pin.kind === 'home' ? 3 : 2 }))
    }
    if (data.path.length) {
      const path = data.path.map(([lng, lat]) => new sdk.LatLng(lat, lng))
      path.forEach(point => bounds.extend(point))
      layers.push(new sdk.Polyline({ map, path, strokeColor: '#23654e', strokeOpacity: 0.85, strokeWeight: 4, strokeStyle: data.estimated ? 'dash' : 'solid' }))
    }
    if (data.pins.length || data.path.length) map.setBounds(bounds, 48, 48, 48, 48)
    return clear
  } catch (error) {
    clear()
    throw error
  }
}

export default function MapCanvas({ data, selected, onSelect }: { data: MapData; selected: string; onSelect: (id: string) => void }) {
  const container = useRef<HTMLDivElement>(null)
  const view = useRef<Promise<{ sdk: Maps; map: KakaoMap }> | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'failed'>('loading')
  const failed = status === 'failed'
  useEffect(() => {
    const node = container.current
    if (!sdkKey || !node || failed) return
    let active = true
    let observer: ResizeObserver | undefined
    function onTileError(event: Event) {
      if (event.target instanceof HTMLImageElement) setStatus('failed')
    }
    node.addEventListener('error', onTileError, true)
    view.current = loadMaps(sdkKey).then(sdk => {
      if (!active) throw new Error('지도가 닫혔어요.')
      const map = new sdk.Map(node, { center: new sdk.LatLng(37.55, 127), level: 6 })
      observer = new ResizeObserver(() => map.relayout())
      observer.observe(node)
      return { sdk, map }
    })
    return () => {
      active = false
      observer?.disconnect()
      node.removeEventListener('error', onTileError, true)
      node.replaceChildren()
      view.current = null
    }
  }, [failed])
  useEffect(() => {
    let active = true
    let clear: (() => void) | undefined
    view.current?.then(({ sdk, map }) => {
      if (!active || failed) return
      clear = drawLayers(sdk, map, data, selected, onSelect)
      setStatus('ready')
    }).catch(() => { if (active) setStatus('failed') })
    return () => { active = false; clear?.() }
  }, [data, selected, onSelect, failed])

  if (!sdkKey || status === 'failed') return <>
    <p className="muted">{status === 'failed' ? '카카오 지도를 불러오지 못했어요.' : '카카오 지도 키가 설정되지 않았어요.'} 좌표 기준 위치도입니다.</p>
    <LocationDiagram data={data} selected={selected} onSelect={onSelect} />
  </>
  return <>{status === 'loading' && <p role="status">카카오 지도를 불러오는 중…</p>}
    <div ref={container} className="kakao-map" aria-label="카카오 후보 지도" /></>
}

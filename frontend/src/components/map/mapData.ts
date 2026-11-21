import type { Comparison, ComparisonHome, Profile, Route } from '../../types/contracts'
import { facilityText, homeTitle, slotName } from '../../utils/home'

export type Point = [number, number] // GeoJSON longitude, latitude
export type Pin = { id: string; point: Point; title: string; badge: string; kind: 'home' | 'destination' | 'facility' }
export function validPoint(point: Point) {
  return Array.isArray(point) && Number.isFinite(point[0]) && Math.abs(point[0]) <= 180 &&
    Number.isFinite(point[1]) && Math.abs(point[1]) <= 90
}

export function routePath(route: Route) {
  const coords = route.geometry?.type === 'LineString' ? route.geometry.coordinates : null
  return (route.status === 'OK' || route.status === 'ESTIMATED') &&
    Array.isArray(coords) && coords.length >= 2 && coords.every(validPoint) ? coords : []
}

export function mapData(result: Comparison, destination: Profile['frequentPlace'], selected: ComparisonHome) {
  const pins: Pin[] = []
  for (const home of result.homes) {
    const { longitude, latitude } = home.location
    pins.push({ id: home.homeId, point: [longitude, latitude], title: homeTitle(home), badge: '후보 ' + slotName(home.slot), kind: 'home' })
  }
  if (destination) pins.push({ id: 'destination', point: [destination.longitude, destination.latitude], title: '기준 목적지 · ' + destination.name, badge: '목적지', kind: 'destination' })
  for (const [index, item] of selected.facilities.entries()) {
    if (item.status === 'FOUND' && item.location) pins.push({
      id: 'facility-' + index, point: [item.location.longitude, item.location.latitude],
      title: facilityText(item), badge: '●', kind: 'facility',
    })
  }
  return { pins: pins.filter(pin => validPoint(pin.point)), path: routePath(selected.route), estimated: selected.route.status === 'ESTIMATED' }
}
export type MapData = ReturnType<typeof mapData>

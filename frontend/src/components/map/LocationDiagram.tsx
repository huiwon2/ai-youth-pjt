import type { MapData, Point } from './mapData'

export default function LocationDiagram({ data, selected, onSelect }: { data: MapData; selected: string; onSelect: (id: string) => void }) {
  const points = [...data.pins.map(pin => pin.point), ...data.path]
  if (!points.length) return <p>표시할 좌표가 없어요.</p>
  let west = Infinity, east = -Infinity, north = -Infinity, south = Infinity
  for (const [lng, lat] of points) {
    west = Math.min(west, lng); east = Math.max(east, lng)
    south = Math.min(south, lat); north = Math.max(north, lat)
  }
  function position([lng, lat]: Point) {
    return [east === west ? 50 : 12 + (lng - west) / (east - west) * 76,
      north === south ? 50 : 12 + (north - lat) / (north - south) * 76]
  }
  return <div className="location-diagram" role="group" aria-label="후보 위치도">
    {data.path.length > 0 && <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"><polyline
      points={data.path.map(p => position(p).join(',')).join(' ')} fill="none" stroke="#23654e" strokeWidth="3"
      strokeDasharray={data.estimated ? '6 5' : undefined} vectorEffect="non-scaling-stroke" /></svg>}
    {data.pins.map(pin => {
      const [x, y] = position(pin.point)
      const style = { left: x + '%', top: y + '%' }
      return pin.kind === 'home' ? <button type="button" key={pin.id} className="map-pin home" style={style} title={pin.title}
        aria-label={pin.title} aria-pressed={selected === pin.id} onClick={() => onSelect(pin.id)}>{pin.badge}</button> :
        <span className={'map-pin ' + pin.kind} key={pin.id} style={style} title={pin.title} aria-label={pin.title}>{pin.badge}</span>
    })}
  </div>
}

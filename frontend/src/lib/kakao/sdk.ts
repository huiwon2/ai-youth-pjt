// The map view only needs this subset of the browser SDK.
type LatLng = object
type Bounds = { extend(point: LatLng): void }
export type KakaoMap = { setBounds(bounds: Bounds, top: number, right: number, bottom: number, left: number): void; relayout(): void }
type Layer = { setMap(map: KakaoMap | null): void }
export type Maps = {
  load(ready: () => void): void
  LatLng: new (latitude: number, longitude: number) => LatLng
  LatLngBounds: new () => Bounds
  Map: new (container: HTMLElement, options: { center: LatLng; level: number }) => KakaoMap
  CustomOverlay: new (options: { map: KakaoMap; position: LatLng; content: HTMLElement; clickable: boolean; yAnchor: number; zIndex: number }) => Layer
  Polyline: new (options: { map: KakaoMap; path: LatLng[]; strokeColor: string; strokeWeight: number; strokeOpacity: number; strokeStyle: string }) => Layer
}

declare global { interface Window { kakao?: { maps?: Maps } } }

let loading: Promise<Maps> | undefined
export function loadMaps(key: string) {
  if (!loading) {
    loading = new Promise<Maps>((resolve, reject) => {
      const script = document.createElement('script')
      let done = false
      const fail = () => {
        if (done) return
        done = true
        window.clearTimeout(timer)
        script.remove()
        reject(new Error('지도 로딩 실패'))
      }
      const timer = window.setTimeout(fail, 10000)
      const ready = () => {
        const sdk = window.kakao?.maps
        if (!sdk?.load) return fail()
        try {
          sdk.load(() => {
            if (done) return
            if (!sdk.Map) return fail()
            done = true
            window.clearTimeout(timer)
            resolve(sdk)
          })
        } catch { fail() }
      }
      if (window.kakao?.maps?.load) ready()
      else {
        script.src = 'https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=' + encodeURIComponent(key)
        script.onload = ready
        script.onerror = fail
        document.head.append(script)
      }
    }).catch(error => { loading = undefined; throw error })
  }
  return loading
}

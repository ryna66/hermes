import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { Challenge } from './types'
export default function ChallengeMap({items}: {items: Challenge[]}) {
  const container = useRef<HTMLDivElement>(null); const [failed,setFailed] = useState(false)
  useEffect(() => {
    if (!container.current || !items.length) return
    setFailed(false)
    const map = L.map(container.current).setView([23.6,85.3],7)
    const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© OpenStreetMap contributors', maxZoom:19})
    tiles.on('tileerror',() => setFailed(true)); tiles.addTo(map)
    const points: L.LatLngTuple[] = []
    items.forEach(c => {
      if (!Number.isFinite(c.latitude) || !Number.isFinite(c.longitude)) return
      const point: L.LatLngTuple = [c.latitude,c.longitude]; points.push(point)
      const link = document.createElement('a'); link.href = `/challenges/${encodeURIComponent(c.id)}`; link.textContent = c.title
      L.circleMarker(point,{radius:7}).addTo(map).bindPopup(link)
    })
    if (points.length) map.fitBounds(L.latLngBounds(points),{padding:[20,20],maxZoom:13})
    return () => {tiles.off(); map.remove()}
  }, [items])
  return <section aria-label="Visible challenges map"><h2>Map of this page</h2>{failed && <p role="status">Map tiles are unavailable. Challenge links and coordinates remain available in the list above.</p>}{items.length ? <div ref={container} aria-label="Challenge locations" style={{height:360}}/> : <p>No locations to show.</p>}</section>
}

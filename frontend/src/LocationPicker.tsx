import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
export default function LocationPicker({latitude, longitude, onChange}: {latitude: string; longitude: string; onChange: (lat: string, lng: string) => void}) {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<L.Map | null>(null)
  const marker = useRef<L.CircleMarker | null>(null)
  const change = useRef(onChange)
  const [failed, setFailed] = useState(false)
  useEffect(() => { change.current = onChange }, [onChange])
  useEffect(() => {
    if (!container.current) return
    const map = L.map(container.current).setView([23.6, 85.3], 7)
    mapRef.current = map
    const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {attribution: '© OpenStreetMap contributors', maxZoom: 19}).addTo(map)
    tiles.on('tileerror', () => setFailed(true))
    map.on('click', (event: L.LeafletMouseEvent) => change.current(String(event.latlng.lat), String(event.latlng.wrap().lng)))
    return () => { tiles.off(); map.remove(); mapRef.current = null; marker.current = null }
  }, [])
  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const lat = Number(latitude), lng = Number(longitude)
    if (!latitude.trim() || !longitude.trim() || !Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
      marker.current?.remove(); marker.current = null; return
    }
    const point: L.LatLngTuple = [lat, lng]
    if (marker.current) marker.current.setLatLng(point)
    else marker.current = L.circleMarker(point, {radius: 7}).addTo(map)
    map.panTo(point)
  }, [latitude, longitude])
  return <section aria-label="Location picker"><p>Click the map to select a location, use your location, or enter coordinates below.</p>{failed && <p role="status">Map tiles are unavailable. You can still enter coordinates or use your location.</p>}<div ref={container} aria-label="Select challenge location" style={{height: 360}}/></section>
}

import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'
import { ErrorDisplay } from './components'
import { categories } from './challenge-options'
import LocationPicker from './LocationPicker'
import type { Category, Challenge, ChallengeInput } from './types'

import { validateChallenge } from './challenge-validation'
import type { ChallengeFields } from './challenge-validation'

function base64(file: File): Promise<string> {
  return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onerror = () => reject(new Error('Unable to read audio.')); reader.onload = () => resolve(String(reader.result).split(',')[1]); reader.readAsDataURL(file) })
}
export default function SubmitChallenge() {
  const [fields, setFields] = useState<ChallengeFields>({title:'', description:'', district:'', affectedPeople:'1', latitude:'', longitude:'', category:''})
  const [photo, setPhoto] = useState<File>(); const [audio, setAudio] = useState<File>()
  const [errors, setErrors] = useState<string[]>([]); const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false); const [transcribing, setTranscribing] = useState(false)
  const [message, setMessage] = useState(''); const [created, setCreated] = useState<Challenge | null>(null)
  const [recording, setRecording] = useState(false); const [starting, setStarting] = useState(false)
  const recorder = useRef<MediaRecorder | null>(null); const stream = useRef<MediaStream | null>(null)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; if (recorder.current?.state === 'recording') recorder.current.stop(); stream.current?.getTracks().forEach(t => t.stop()) } }, [])
  const change = (key: keyof ChallengeFields, value: string) => setFields(f => ({...f, [key]:value}))
  const location = (latitude: string, longitude: string) => setFields(f => ({...f, latitude, longitude}))
  async function record() {
    setError(null); setStarting(true)
    try {
      if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) throw new Error('Recording is unavailable in this browser. Upload an audio file instead.')
      const media = await navigator.mediaDevices.getUserMedia({audio:true})
      if (!mounted.current) { media.getTracks().forEach(t => t.stop()); return }
      stream.current = media
      const instance = new MediaRecorder(media); recorder.current = instance
      const chunks: Blob[] = []
      instance.ondataavailable = event => { if (event.data.size) chunks.push(event.data) }
      instance.onstop = () => {
        media.getTracks().forEach(t => t.stop())
        if (mounted.current) {
          const type = (instance.mimeType || chunks[0]?.type || 'audio/webm').split(';')[0]
          setAudio(new File(chunks, 'voice-note', {type})); setRecording(false)
        }
      }
      instance.onerror = () => { media.getTracks().forEach(t => t.stop()); if (mounted.current) { setRecording(false); setError(new Error('Recording failed. Upload audio or enter the description manually.')) } }
      instance.start(); setRecording(true)
    } catch (err) { stream.current?.getTracks().forEach(t => t.stop()); if (mounted.current) setError(err) }
    finally { if (mounted.current) setStarting(false) }
  }
  async function transcribe() {
    setError(null); setMessage('')
    if (!audio || audio.size < 1 || audio.size > 1048576 || !/^audio\/[A-Za-z0-9.+-]+$/.test(audio.type.split(';')[0])) {
      setError(new Error('Choose audio containing 1 to 1048576 bytes with an audio media type to transcribe.')); return
    }
    setTranscribing(true)
    try {
      const result = await api.transcribe({audioBase64: await base64(audio), mimeType: audio.type.split(';')[0], language:'en'})
      if (result.status !== 'MANUAL_ENTRY' && result.text?.trim()) change('description', result.text)
      setMessage(result.message)
    } catch (err) { setError(err); setMessage('Please enter the problem description manually.') }
    finally { setTranscribing(false) }
  }
  async function submit() {
    setError(null); const invalid = validateChallenge(fields, photo, audio); setErrors(invalid)
    if (invalid.length) return
    setBusy(true)
    const input: ChallengeInput = {...fields, latitude:Number(fields.latitude), longitude:Number(fields.longitude), affectedPeople:Number(fields.affectedPeople), category: fields.category ? fields.category as Category : null}
    try { setCreated(await api.submitChallenge(input, photo, audio)) } catch (err) { setError(err) } finally { setBusy(false) }
  }
  if (created) return <><h1>Challenge submitted</h1><p role="status">AI suggestion status: {created.aiStatus}</p><Link to={`/challenges/${encodeURIComponent(created.id)}`}>View your challenge</Link></>
  return <><h1>Submit challenge</h1><ErrorDisplay error={error}/>{errors.length > 0 && <ul role="alert">{errors.map(e => <li key={e}>{e}</li>)}</ul>}<form noValidate onSubmit={e => {e.preventDefault(); void submit()}}><fieldset disabled={busy}><legend>Problem details</legend>
    <label>Title <input required maxLength={255} value={fields.title} onChange={e => change('title', e.target.value)}/></label>
    <label>Description <textarea required value={fields.description} onChange={e => change('description', e.target.value)}/></label>
    <label>District <input required maxLength={255} value={fields.district} onChange={e => change('district', e.target.value)}/></label>
    <label>Affected people <input required type="number" min={1} max={2147483647} step={1} value={fields.affectedPeople} onChange={e => change('affectedPeople', e.target.value)}/></label>
    <label>Category (optional) <select value={fields.category} onChange={e => change('category', e.target.value)}><option value="">No category suggestion</option>{categories.map(c => <option key={c}>{c}</option>)}</select></label>
    <LocationPicker latitude={fields.latitude} longitude={fields.longitude} onChange={location}/>
    <button type="button" onClick={() => {setError(null); if (!navigator.geolocation) {setError(new Error('Geolocation is unavailable. Enter coordinates or select the map.')); return} navigator.geolocation.getCurrentPosition(p => location(String(p.coords.latitude), String(p.coords.longitude)), e => setError(new Error(`Unable to get your location: ${e.message}`)))}}>Use my location</button>
    <label>Latitude <input required type="number" min={-90} max={90} step="any" value={fields.latitude} onChange={e => change('latitude', e.target.value)}/></label>
    <label>Longitude <input required type="number" min={-180} max={180} step="any" value={fields.longitude} onChange={e => change('longitude', e.target.value)}/></label>
    <label>Photo (optional) <input type="file" accept="image/*" onChange={e => setPhoto(e.target.files?.[0])}/></label>
    <fieldset disabled={transcribing || recording || starting}><legend>Voice note (optional)</legend><label>Upload audio <input type="file" accept="audio/*" onChange={e => {setAudio(e.target.files?.[0]); setMessage('')}}/></label><button type="button" onClick={() => void record()}>Record audio</button><button type="button" disabled={!audio} onClick={() => void transcribe()}>{transcribing ? 'Transcribing…' : 'Transcribe audio'}</button></fieldset>
    {recording && <button type="button" onClick={() => recorder.current?.stop()}>Stop recording</button>}
    {audio && <p>Selected audio: {audio.name} ({audio.size} bytes). This audio will be attached to the challenge.</p>}{audio && <button type="button" disabled={recording || transcribing} onClick={() => {setAudio(undefined); setMessage('')}}>Remove audio</button>}
    <p>Photo and audio uploads support up to 1 MiB each. Transcription supports audio up to 1 MiB, in English. You can edit the description after transcription.</p>{message && <p role="status">{message}</p>}
    <button disabled={transcribing || recording || starting}>{busy ? 'Submitting…' : 'Submit challenge'}</button>
  </fieldset></form></>
}

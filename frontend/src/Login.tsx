import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { api } from './api'
import { useAuth } from './auth-context'
import { ErrorDisplay, Loading } from './components'
export default function Login() {
  const auth = useAuth(); const location = useLocation()
  const [register, setRegister] = useState(false); const [phone, setPhone] = useState(''); const [name, setName] = useState(''); const [otp, setOtp] = useState('')
  const [expires, setExpires] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>(null)
  if (auth.loading) return <Loading/>
  if (auth.user) { const from = location.state?.from; return <Navigate to={typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') && from !== '/login' ? from : '/'} replace/> }
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError(null)
    try {
      if (expires) await auth.login(await api.verifyOtp(phone, otp))
      else { const result = register ? await api.register(name, phone) : await api.requestOtp(phone); setExpires(result.expiresAt) }
    } catch (err) { setError(err) } finally { setBusy(false) }
  }
  return <><h1>{register ? 'Citizen registration' : 'Login'}</h1><p>In dev, the OTP is printed in the core service console.</p><ErrorDisplay error={error}/>
    <form onSubmit={submit}><fieldset disabled={busy}><legend>{expires ? 'Verify OTP' : 'Request OTP'}</legend>
      {register && !expires && <label>Name <input required maxLength={200} value={name} onChange={e => setName(e.target.value)}/></label>}
      <label>Phone <input type="tel" autoComplete="tel" required pattern="\+?[0-9]{10,15}" value={phone} readOnly={!!expires} onChange={e => setPhone(e.target.value)}/></label>
      {expires && <><p>OTP expires at {new Date(expires).toLocaleString()}.</p><label>OTP <input required inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" value={otp} onChange={e => setOtp(e.target.value)}/></label></>}
      <button type="submit">{expires ? 'Verify and log in' : register ? 'Register and request OTP' : 'Request OTP'}</button>
    </fieldset></form>{busy && <Loading/>}
    <button disabled={busy} onClick={() => {setExpires(''); setOtp(''); setError(null); if (!expires) setRegister(!register)}}>{expires ? 'Start again / request a new OTP' : register ? 'Use existing account' : 'Register as a citizen'}</button>
  </>
}

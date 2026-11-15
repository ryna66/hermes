import type { Token } from './types'
const key = 'sih.auth'
export const sessionChanged = 'sih-session-changed'
export function clearSession() { sessionStorage.removeItem(key); window.dispatchEvent(new Event(sessionChanged)) }
export function loadSession(): Token | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) ?? 'null') as Token | null
    if (value && typeof value.accessToken === 'string' && value.accessToken && value.tokenType === 'Bearer' && Number.isFinite(Date.parse(value.expiresAt)) && Date.parse(value.expiresAt) > Date.now()) return value
  } catch { /* Discard invalid stored data. */ }
  sessionStorage.removeItem(key)
  return null
}
export function saveSession(value: Token) { sessionStorage.setItem(key, JSON.stringify(value)); window.dispatchEvent(new Event(sessionChanged)) }
export function tokenForRequest() {
  const token = loadSession()
  if (!token) window.dispatchEvent(new Event(sessionChanged))
  return token?.accessToken
}
export function scheduleExpiry(token: Token, expire = clearSession): () => void {
  let timer: ReturnType<typeof setTimeout>
  const check = () => {
    const remaining = Date.parse(token.expiresAt) - Date.now()
    if (!Number.isFinite(remaining) || remaining <= 0) expire()
    else timer = setTimeout(check, Math.min(remaining, 2147483647))
  }
  check()
  return () => clearTimeout(timer)
}

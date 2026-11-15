import { AuthContext } from './auth-context'
import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import type { Account, Token } from './types'
import { api } from './api'
import { clearSession, loadSession, saveSession, scheduleExpiry, sessionChanged } from './session'
import { ErrorDisplay } from './components'
export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(loadSession)
  const [user, setUser] = useState<Account | null>(null)
  const [loading, setLoading] = useState(!!token)
  const [error, setError] = useState<unknown>(null)
  const [retry, setRetry] = useState(0)
  useEffect(() => { const sync = () => setToken(loadSession()); window.addEventListener(sessionChanged, sync); return () => window.removeEventListener(sessionChanged, sync) }, [])
  useEffect(() => {
    let active = true
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
    if (!token) { setUser(null); setLoading(false); setError(null); return }
    setUser(null); setLoading(true); setError(null)
    api.me().then(value => { if (active) setUser(value) }).catch(err => { if (active) setError(err) }).finally(() => { if (active) setLoading(false) })
    const cancel = scheduleExpiry(token)
    return () => { active = false; cancel() }
  }, [token, retry])
  const login = useCallback(async (value: Token) => { saveSession(value) }, [])
  return <AuthContext.Provider value={{user, loading, login, logout: clearSession}}>{error ? <><ErrorDisplay error={error}/><button onClick={() => setRetry(n => n + 1)}>Retry account lookup</button><button onClick={clearSession}>Log out</button></> : children}</AuthContext.Provider>
}

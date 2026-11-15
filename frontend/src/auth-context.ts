import { createContext, useContext } from 'react'
import type { Account, Token } from './types'
export interface AuthState { user: Account | null; loading: boolean; login: (token: Token) => Promise<void>; logout: () => void }
export const AuthContext = createContext<AuthState | null>(null)
export function useAuth() { const auth = useContext(AuthContext); if (!auth) throw new Error('AuthProvider required'); return auth }

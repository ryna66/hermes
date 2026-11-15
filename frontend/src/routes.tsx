import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './auth-context'
import { Loading } from './components'
import type { Role } from './types'
export function RoleGuard({ roles }: { roles: Role[] }) {
  const {user, loading} = useAuth(); const location = useLocation()
  if (loading) return <Loading/>
  if (!user) return <Navigate to="/login" state={{from: location.pathname}} replace/>
  return roles.includes(user.role) ? <Outlet/> : <Navigate to="/not-allowed" replace/>
}

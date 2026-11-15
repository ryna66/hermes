import { useEffect, useState } from 'react'
import { BrowserRouter, Link, Outlet, Route, Routes, useParams } from 'react-router-dom'
import { AuthProvider } from './auth'
import { useAuth } from './auth-context'
import { api } from './api'
import { ErrorDisplay } from './components'
import { RoleGuard } from './routes'
import { pages } from './page-routes'
import Login from './Login'
import { Landing, Explorer } from './public-pages'
import ChallengeDetail from './ChallengeDetail'
import SubmitChallenge from './SubmitChallenge'
import { MyChallenges, Notifications } from './citizen-pages'
import { AccountProvisioning, AdminQueue } from './admin-pages'
import { DepartmentProfile, ProjectWorkspace, UniversityInbox } from './university-pages'
import { IndustryDashboard } from './industry-pages'
import { GovernmentAnalytics } from './government-pages'
function NotificationsBadge() {
  const {user} = useAuth(); const [count,setCount] = useState<number | null>(null); const [error,setError] = useState<unknown>(null)
  useEffect(() => {
    if (!user) return
    let active = true; let running = false
    const poll = async () => {
      if (running) return
      running = true
      try {
        let page = 0, unread = 0, last = false
        while (!last && active) { const result = await api.notifications({page, size: 100}); unread += result.content.filter(n => !n.readAt).length; last = result.last; page++ }
        if (active) { setCount(unread); setError(null) }
      } catch (err) { if (active) setError(err) } finally { running = false }
    }
    const changed = () => void poll(); window.addEventListener('notifications-changed', changed)
    void poll(); const timer = setInterval(() => void poll(), 60000)
    return () => {active = false; clearInterval(timer); window.removeEventListener('notifications-changed', changed)}
  }, [user])
  return <><span role="status">Notifications: {error ? 'unavailable' : count ?? 'loading' }{!error && count !== null ? ' unread' : ''}</span><ErrorDisplay error={error}/></>
}
function Layout() {
  const {user, logout} = useAuth()
  return <><header><p>Societal Innovation Platform</p><nav aria-label="Main"><ul>{pages.filter(p => !p.path.includes(':') && (!p.roles || (user && p.roles.includes(user.role)))).map(p => <li key={p.path}><Link to={p.path}>{p.title}</Link></li>)}</ul></nav>{user ? <><p>{user.name} ({user.role})</p><button onClick={logout}>Log out</button><NotificationsBadge key={user.id}/></> : <Link to="/login">Login / Register</Link>}</header><main><Outlet/></main></>
}
function Placeholder({title}: {title: string}) { const params = useParams(); return <><h1>{title}</h1><p>This page will be implemented in a later milestone.</p>{Object.entries(params).map(([key,value]) => <p key={key}>{key}: {value}</p>)}</> }
function PageContent({path, title}: {path: string; title: string}) {
  switch (path) {
    case '/': return <Landing/>
    case '/explore': return <Explorer/>
    case '/challenges/:id': return <ChallengeDetail/>
    case '/submit': return <SubmitChallenge/>
    case '/my-challenges': return <MyChallenges/>
    case '/notifications': return <Notifications/>
    case '/admin/validation': return <AdminQueue/>
    case '/admin/allocation': return <AdminQueue initialQueue="allocation"/>
    case '/admin/accounts': return <AccountProvisioning/>
    case '/universities/:id/departments': return <DepartmentProfile/>
    case '/university/inbox': return <UniversityInbox/>
    case '/projects/:id': return <ProjectWorkspace/>
    case '/industry': return <IndustryDashboard/>
    case '/government': return <GovernmentAnalytics/>
    default: return <Placeholder title={title}/>
  }
}
export function AppRoutes() {
  return <Routes><Route element={<Layout/>}><Route path="/login" element={<Login/>}/><Route path="/not-allowed" element={<><h1>Not allowed</h1><p>Your role cannot access this page.</p><Link to="/">Return home</Link></>}/>{pages.map(p => p.roles ? <Route key={p.path} element={<RoleGuard roles={p.roles}/>}><Route path={p.path} element={<PageContent path={p.path} title={p.title}/>}/></Route> : <Route key={p.path} path={p.path} element={<PageContent path={p.path} title={p.title}/>}/>)}<Route path="*" element={<h1>Page not found</h1>}/></Route></Routes>
}
export default function App() { return <BrowserRouter><AuthProvider><AppRoutes/></AuthProvider></BrowserRouter> }

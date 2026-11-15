import { pages } from './page-routes'
import { AppRoutes } from './App'
import { act, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom'
import { api, request } from './api'
import { clearSession, loadSession, saveSession, scheduleExpiry } from './session'
import { AuthProvider } from './auth'
import { AuthContext, useAuth } from './auth-context'
import { RoleGuard } from './routes'
import type { Role } from './types'
const roles: Role[] = ['CITIZEN', 'ADMIN', 'UNIVERSITY', 'INDUSTRY', 'GOVERNMENT']
const token = () => ({ accessToken: 'test-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 60000).toISOString() })
beforeEach(() => sessionStorage.clear())
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals() })
describe('API', () => {
  it('parses backend validation errors and attaches the bearer token', async () => {
    saveSession(token())
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({status:400, code:'BAD_REQUEST', message:'Invalid input', fieldErrors:[{field:'phone',message:'Invalid phone'}]}), {status:400}))
    vi.stubGlobal('fetch', fetcher)
    await expect(api.requestOtp('bad')).rejects.toMatchObject({status:400,code:'BAD_REQUEST',message:'Invalid input',fieldErrors:[{field:'phone',message:'Invalid phone'}]})
    const [url, init] = fetcher.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/auth/otp/request')
    expect(init.headers.get('Authorization')).toBe('Bearer test-token')
    expect(JSON.parse(init.body)).toEqual({phone:'bad'})
  })
  it('handles non-JSON failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>unavailable</html>', {status:502})))
    await expect(request('/test')).rejects.toMatchObject({status:502,code:'HTTP_ERROR',message:'Request failed (502)',fieldErrors:[]})
  })
  it('clears the session on 401', async () => {
    saveSession(token()); vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', {status:401})))
    await expect(api.me()).rejects.toMatchObject({status:401}); expect(loadSession()).toBeNull()
  })
  it('preserves the session on 403 and sends multipart without overriding its boundary', async () => {
    saveSession(token()); const fetcher = vi.fn().mockResolvedValue(new Response('{}',{status:403})); vi.stubGlobal('fetch',fetcher)
    await expect(api.submitChallenge({title:'A',description:'B',latitude:1,longitude:2,district:'D'},new File(['image'],'a.png'))).rejects.toMatchObject({status:403})
    expect(loadSession()).not.toBeNull(); expect(fetcher.mock.calls[0][1].headers.has('Content-Type')).toBe(false)
    expect(fetcher.mock.calls[0][1].body.get('challenge')).toBeInstanceOf(Blob)
  })
})
describe('session expiry', () => {
  it('restores valid sessions and discards malformed or expired sessions', () => {
    saveSession(token()); expect(loadSession()?.accessToken).toBe('test-token')
    sessionStorage.setItem('sih.auth','broken'); expect(loadSession()).toBeNull()
    saveSession({...token(),expiresAt:new Date(Date.now()-1).toISOString()}); expect(loadSession()).toBeNull()
  })
  it('automatically expires at the boundary and supports cleanup', () => {
    vi.useFakeTimers(); const value = token(); saveSession(value); const cancel = scheduleExpiry(value)
    vi.advanceTimersByTime(59999); expect(loadSession()).not.toBeNull()
    vi.advanceTimersByTime(1); expect(loadSession()).toBeNull(); cancel()
    const expire = vi.fn(); const stop = scheduleExpiry(token(),expire); stop(); vi.advanceTimersByTime(60000); expect(expire).not.toHaveBeenCalled()
  })
  it('loads the role using /users/me and removes authenticated UI on timed expiry', async () => {
    vi.useFakeTimers(); saveSession(token()); vi.spyOn(api,'me').mockResolvedValue({id:'1',name:'Admin',role:'ADMIN'})
    function Probe() { const auth=useAuth(); return <p>{auth.loading ? 'loading' : auth.user?.role ?? 'anonymous'}</p> }
    render(<AuthProvider><Probe/></AuthProvider>); await act(async () => {})
    expect(screen.getByText('ADMIN')).toBeTruthy()
    await act(async () => { vi.advanceTimersByTime(60000) }); expect(screen.getByText('anonymous')).toBeTruthy()
  })
  it('removes authenticated UI after an API 401', async () => {
    saveSession(token()); vi.spyOn(api,'me').mockResolvedValue({id:'1',name:'Admin',role:'ADMIN'})
    function Probe() {const auth=useAuth(); return <p>{auth.user?.role ?? 'anonymous'}</p>}
    render(<AuthProvider><Probe/></AuthProvider>); await waitFor(() => expect(screen.getByText('ADMIN')).toBeTruthy())
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response('{}',{status:401})))
    await act(async () => { await request('/test').catch(() => {}) }); expect(screen.getByText('anonymous')).toBeTruthy()
  })
})
describe('role guards', () => {
  for (const required of roles) for (const actual of roles) it(`${required} route with ${actual} account`, () => {
    render(<AuthContext.Provider value={{user:{id:'1',name:'User',role:actual},loading:false,login:async()=>{},logout:clearSession}}><MemoryRouter initialEntries={['/protected']}><Routes><Route element={<RoleGuard roles={[required]}/>}><Route path='/protected' element={<p>Protected content</p>}/></Route><Route path='/not-allowed' element={<p>Not allowed</p>}/></Routes></MemoryRouter></AuthContext.Provider>)
    expect(screen.getByText(required === actual ? 'Protected content' : 'Not allowed')).toBeTruthy()
  })
  for (const loading of [false,true]) it(`anonymous access while loading=${loading}`, () => {
    render(<AuthContext.Provider value={{user:null,loading,login:async()=>{},logout:clearSession}}><MemoryRouter initialEntries={['/protected']}><Routes><Route element={<RoleGuard roles={roles}/>}><Route path='/protected' element={<Outlet/>}/></Route><Route path='/login' element={<p>Login required</p>}/></Routes></MemoryRouter></AuthContext.Provider>)
    expect(screen.getByText(loading ? 'Loading…' : 'Login required')).toBeTruthy()
  })
})

describe('19a redirects with actual session state',()=>{
  function protectedPage() {
    return render(<MemoryRouter initialEntries={['/protected']}><AuthProvider><Routes><Route element={<RoleGuard roles={['ADMIN']}/>}><Route path='/protected' element={<p>Protected page</p>}/></Route><Route path='/login' element={<p>Login page</p>}/></Routes></AuthProvider></MemoryRouter>)
  }
  it('redirects an expired stored token to login without fetching an account',async()=>{
    saveSession({...token(),expiresAt:new Date(Date.now()-1000).toISOString()});const me=vi.spyOn(api,'me');protectedPage()
    await screen.findByText('Login page');expect(me).not.toHaveBeenCalled()
  })
  it('redirects to login after a protected request returns 401',async()=>{
    saveSession(token());vi.spyOn(api,'me').mockResolvedValue({id:'1',name:'Admin',role:'ADMIN'});protectedPage();await screen.findByText('Protected page')
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({message:'Expired credentials'}),{status:401})))
    await act(async()=>{await request('/government/analytics').catch(()=>{})});await screen.findByText('Login page');expect(loadSession()).toBeNull()
  })
})

describe('19a actual protected page guards',()=>{
  for (const page of pages.filter(p=>p.roles)) for (const role of roles.filter(r=>!page.roles!.includes(r))) it(`${role} reaches not allowed for ${page.path}`,async()=>{
    vi.spyOn(api,'notifications').mockResolvedValue({content:[],number:0,size:100,totalPages:0,totalElements:0,last:true})
    render(<AuthContext.Provider value={{user:{id:'1',name:'Reader',role},loading:false,login:async()=>{},logout:clearSession}}><MemoryRouter initialEntries={[page.path.replace(':id','test-id')]}><AppRoutes/></MemoryRouter></AuthContext.Provider>)
    await screen.findByRole('heading',{name:'Not allowed'})
  })
})

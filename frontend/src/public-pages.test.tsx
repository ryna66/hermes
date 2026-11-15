import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { api, ApiError } from './api'
import { AuthContext } from './auth-context'
import type { Detail, Page, Role, Status } from './types'
import { Explorer, Landing } from './public-pages'
import { explorerFilters } from './explorer-filters'
import ChallengeDetail, { Timeline } from './ChallengeDetail'
const leaflet = vi.hoisted(() => ({tileError: undefined as undefined | (() => void)}))
vi.mock('leaflet', () => ({default:{map:()=>({setView(){return this},fitBounds:vi.fn(),remove:vi.fn()}),tileLayer:()=>({on:(_name:string,fn:()=>void)=>{leaflet.tileError=fn},addTo:vi.fn(),off:vi.fn()}),circleMarker:()=>({addTo(){return this},bindPopup:vi.fn()}),latLngBounds:vi.fn()}}))
afterEach(() => vi.restoreAllMocks())
const detail: Detail = {id:'c1',title:'Unsafe water',description:'Broken village borewell',district:'Gumla',latitude:23,longitude:84,category:'WATER_SANITATION',affectedPeople:12,status:'SUBMITTED',hasPhoto:false,hasAudio:false,fixedConfirmedAt:null,createdAt:'2026-11-01T00:00:00Z',aiStatus:'ENRICHED',allowedTransitions:[],timeline:[]}
function page<T>(content:T[], number=0,last=true):Page<T> {return {content,number,last,size:20,totalElements:content.length,totalPages:last ? 1 : 2}}
function show(d=detail,role:Role | null='ADMIN') {
  vi.spyOn(api,'challenge').mockResolvedValue(d)
  render(<AuthContext.Provider value={{user:role ? {id:'u1',name:'User',role} : null,loading:false,login:async()=>{},logout:()=>{}}}><MemoryRouter initialEntries={['/challenges/c1']}><Routes><Route path='/challenges/:id' element={<ChallengeDetail/>}/></Routes></MemoryRouter></AuthContext.Provider>)
}
describe('public pages', () => {
  it('requests recent challenges on landing', async () => {
    const spy=vi.spyOn(api,'challenges').mockResolvedValue(page([detail])); render(<MemoryRouter><Landing/></MemoryRouter>)
    expect(await screen.findByRole('link',{name:'Unsafe water'})).toBeTruthy(); expect(spy).toHaveBeenCalledWith({page:0,size:5})
    expect(screen.getByRole('link',{name:'Submit a challenge'}).getAttribute('href')).toBe('/submit')
  })
  it('normalizes filters and builds the actual API query',async()=>{
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify(page([])))))
    const filters=explorerFilters(new URLSearchParams('district=East+Singhbhum&category=HEALTH&status=VALIDATED&page=2'))
    await api.challenges(filters)
    const url = new URL(vi.mocked(fetch).mock.calls[0][0] as string)
    expect(Object.fromEntries(url.searchParams)).toEqual({district:'East Singhbhum',category:'HEALTH',status:'VALIDATED',page:'2',size:'20'})
    expect(explorerFilters(new URLSearchParams('category=invalid&status=invalid&page=-1'))).toEqual({district:undefined,category:undefined,status:undefined,page:0,size:20})
    vi.unstubAllGlobals()
  })
  it('applies filters, resets pagination and preserves filters on next page',async()=>{
    const spy=vi.spyOn(api,'challenges').mockResolvedValue(page([detail],0,false))
    render(<MemoryRouter initialEntries={['/explore?page=3']}><Explorer/></MemoryRouter>); await screen.findByText('Broken village borewell')
    fireEvent.change(screen.getByLabelText('District'),{target:{value:' Gumla '}});fireEvent.change(screen.getByLabelText('Category'),{target:{value:'WATER_SANITATION'}}); fireEvent.click(screen.getByText('Apply filters'))
    await waitFor(()=>expect(spy).toHaveBeenLastCalledWith({district:'Gumla',category:'WATER_SANITATION',status:undefined,page:0,size:20}))
    fireEvent.click(await screen.findByText('Next')); await waitFor(()=>expect(spy).toHaveBeenLastCalledWith({district:'Gumla',category:'WATER_SANITATION',status:undefined,page:1,size:20}))
  })
  it('keeps the list available on tile failure',async()=>{
    vi.spyOn(api,'challenges').mockResolvedValue(page([detail]));render(<MemoryRouter><Explorer/></MemoryRouter>);await screen.findByText('Broken village borewell')
    leaflet.tileError?.(); expect(await screen.findByText(/Map tiles are unavailable/)).toBeTruthy(); expect(screen.getByRole('link',{name:'Unsafe water'})).toBeTruthy()
  })
  it('shows list load errors',async()=>{
    vi.spyOn(api,'challenges').mockRejectedValue(new ApiError(503,{message:'Service unavailable'}));render(<MemoryRouter><Explorer/></MemoryRouter>);expect(await screen.findByRole('alert')).toHaveProperty('textContent',expect.stringContaining('Service unavailable'))
  })
})
describe('challenge detail',()=>{
  it('renders timeline chronologically without mutating the response',()=>{
    const events:Detail['timeline']=[{fromState:'SUBMITTED',toState:'VALIDATED',createdAt:'2026-11-02T00:00:00Z'},{fromState:null,toState:'SUBMITTED',createdAt:'2026-11-01T00:00:00Z'}]
    render(<Timeline events={events}/>);const rows=screen.getAllByRole('listitem');expect(rows[0].textContent).toContain('New report → SUBMITTED');expect(rows[1].textContent).toContain('SUBMITTED → VALIDATED');expect(events[0].toState).toBe('VALIDATED')
  })
  it('uses exactly allowedTransitions rather than deriving actions from status or role',async()=>{
    show({...detail,allowedTransitions:['PILOT']},'ADMIN');await screen.findByRole('heading',{name:'Unsafe water'});expect(screen.getByRole('button',{name:'Move to PILOT'})).toBeTruthy();expect(screen.queryByRole('button',{name:'Move to VALIDATED'})).toBeNull()
  })
  it('has no transition buttons for an empty allowlist',async()=>{
    show(detail,null);await screen.findByText('No lifecycle actions available.');expect(screen.queryByRole('button',{name:/Move to/})).toBeNull()
  })
  for(const status of [403,409])it(`shows the backend ${status} message after transition failure`,async()=>{
    show({...detail,allowedTransitions:['REJECTED']});vi.spyOn(api,'transition').mockRejectedValue(new ApiError(status,{code:status===403?'FORBIDDEN':'CONFLICT',message:'Backend rejected this action'}))
    fireEvent.click(await screen.findByRole('button',{name:'Move to REJECTED'}));fireEvent.change(screen.getByLabelText('Reason (required)'),{target:{value:'Invalid report'}});fireEvent.click(screen.getByText('Submit action'))
    expect((await screen.findByRole('alert')).textContent).toContain('Backend rejected this action');expect(api.transition).toHaveBeenCalledWith('c1',expect.objectContaining({toState:'REJECTED',reason:'Invalid report'}))
  })
  for(const target of ['ASSIGNED','PROPOSAL_CREATED','INDUSTRY_CONNECTED'] as Status[])it(`uses the dedicated endpoint for ${target}`,async()=>{
    show({...detail,allowedTransitions:[target]});const assign=vi.spyOn(api,'assign').mockResolvedValue(detail);const create=vi.spyOn(api,'createProject').mockResolvedValue({} as never);const pledge=vi.spyOn(api,'pledge').mockResolvedValue({} as never);vi.spyOn(api,'project').mockResolvedValue({id:'p1',challengeId:'c1'} as never)
    fireEvent.click(await screen.findByRole('button',{name:`Move to ${target}`}))
    if(target==='ASSIGNED')fireEvent.change(screen.getByLabelText('University ID'),{target:{value:'org1'}})
    if(target==='PROPOSAL_CREATED')for(const label of ['Project title','Proposal','Team name'])fireEvent.change(screen.getByLabelText(label),{target:{value:label}})
    if(target==='INDUSTRY_CONNECTED'){fireEvent.change(screen.getByLabelText('Project ID'),{target:{value:'p1'}});fireEvent.change(screen.getByLabelText('Support type'),{target:{value:'MENTORSHIP'}});fireEvent.change(screen.getByLabelText('Support description'),{target:{value:'Expert help'}})}
    fireEvent.click(screen.getByText('Submit action'));await waitFor(()=>expect(target==='ASSIGNED'?assign:target==='PROPOSAL_CREATED'?create:pledge).toHaveBeenCalled())
  })
  it('shows confirm fixed only after verifying citizen ownership across pages and refreshes after success',async()=>{
    vi.spyOn(api,'ownChallenges').mockResolvedValueOnce(page([],0,false)).mockResolvedValueOnce(page([{id:'c1',title:'Own',status:'DEPLOYED' as const,category:null,affectedPeople:1,fixedConfirmedAt:null,rejectionReason:null}],1));vi.spyOn(api,'confirmFixed').mockResolvedValue(detail)
    show({...detail,status:'DEPLOYED'},'CITIZEN');const button=await screen.findByRole('button',{name:'Confirm fixed'});expect(api.ownChallenges).toHaveBeenLastCalledWith({page:1,size:100});fireEvent.click(button);await waitFor(()=>expect(api.confirmFixed).toHaveBeenCalledWith('c1'))
  })
  it('hides confirm fixed for a foreign report',async()=>{
    vi.spyOn(api,'ownChallenges').mockResolvedValue(page([]));show({...detail,status:'DEPLOYED'},'CITIZEN');await screen.findByRole('heading',{name:'Unsafe water'});expect(screen.queryByRole('button',{name:'Confirm fixed'})).toBeNull()
  })
})

describe('19a detail transition contracts', () => {
  for (const target of ['VALIDATED','CATEGORIZED','REJECTED','PROTOTYPE','PILOT','DEPLOYED','IMPACT_EVALUATED'] as Status[]) it(`submits and refreshes allowed ${target}`, async () => {
    show({...detail, allowedTransitions:[target]})
    const transition=vi.spyOn(api,'transition').mockResolvedValue(detail)
    fireEvent.click(await screen.findByRole('button',{name:`Move to ${target}`}))
    if (target==='CATEGORIZED') fireEvent.change(screen.getByLabelText('Category'),{target:{value:'HEALTH'}})
    if (['REJECTED','PILOT','DEPLOYED'].includes(target)) fireEvent.change(screen.getByLabelText('Reason (required)'),{target:{value:'Reviewed evidence'}})
    if (target==='IMPACT_EVALUATED') fireEvent.change(screen.getByLabelText('Impact summary'),{target:{value:'100 people helped'}})
    vi.mocked(api.challenge).mockResolvedValue({...detail,status:target,allowedTransitions:[]})
    fireEvent.click(screen.getByText('Submit action'))
    await screen.findByText('No lifecycle actions available.')
    expect(transition).toHaveBeenCalledWith('c1',expect.objectContaining({toState:target,...(target==='CATEGORIZED'?{category:'HEALTH'}:{}),...(target==='IMPACT_EVALUATED'?{impactSummary:'100 people helped'}:{})}))
    expect(api.challenge).toHaveBeenCalledTimes(2)
  })
  it('omits stale funding values after changing support type', async () => {
    show({...detail,allowedTransitions:['INDUSTRY_CONNECTED']},'INDUSTRY')
    vi.spyOn(api,'project').mockResolvedValue({id:'p1',challengeId:'c1'} as never)
    const pledge=vi.spyOn(api,'pledge').mockResolvedValue({} as never)
    fireEvent.click(await screen.findByRole('button',{name:'Move to INDUSTRY_CONNECTED'}))
    fireEvent.change(screen.getByLabelText('Project ID'),{target:{value:'p1'}})
    fireEvent.change(screen.getByLabelText('Amount (required for funding)'),{target:{value:'25'}})
    fireEvent.change(screen.getByLabelText('Currency (required for funding)'),{target:{value:'INR'}})
    fireEvent.change(screen.getByLabelText('Support type'),{target:{value:'MENTORSHIP'}})
    expect(screen.queryByLabelText('Amount (required for funding)')).toBeNull()
    fireEvent.change(screen.getByLabelText('Support description'),{target:{value:'Expert advice'}})
    fireEvent.click(screen.getByText('Submit action'))
    await waitFor(()=>expect(pledge).toHaveBeenCalledWith('p1',{supportType:'MENTORSHIP',description:'Expert advice',amount:undefined,currency:undefined}))
  })
  it('handles all nullable detail fields and nullable first timeline state', async () => {
    show({...detail,category:null,fixedConfirmedAt:null,timeline:[{fromState:null,toState:'SUBMITTED',createdAt:detail.createdAt}]},null)
    await screen.findByText('Uncategorized');expect(screen.getByText(/New report → SUBMITTED/)).toBeTruthy()
  })
})

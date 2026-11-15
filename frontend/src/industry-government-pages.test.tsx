import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { ReactNode } from 'react'
import { api, ApiError } from './api'
import { AuthContext } from './auth-context'
import { IndustryDashboard } from './industry-pages'
import { CountChart, GovernmentAnalytics } from './government-pages'
import PledgeForm from './PledgeForm'
import { ProjectWorkspace } from './university-pages'
import type { Dashboard, Detail, Page, Project, Role, Status, SupportView } from './types'

afterEach(() => vi.restoreAllMocks())
const project: Project={id:'p1',challengeId:'c1',universityId:'org1',title:'Water project',proposalText:null,impactSummary:'Safer water',status:'PROPOSAL_CREATED',team:null,milestones:[{id:'m1',title:'Survey',description:'Visit site',dueDate:'2026-12-01',status:'COMPLETED',outcome:'Survey completed',completedAt:'2026-11-16T00:00:00Z'}]}
const pledge: SupportView={id:'s1',projectId:'p1',industryOrganizationId:'i1',supportType:'FUNDING',amount:100,currency:'INR',description:'Repair support'}
const detail: Detail={id:'c1',title:'Water report',description:'Repair borewell',district:'Gumla',latitude:23,longitude:84,category:'WATER_SANITATION',affectedPeople:12,status:'PROPOSAL_CREATED',hasPhoto:false,hasAudio:false,fixedConfirmedAt:null,createdAt:'2026-11-01T00:00:00Z',aiStatus:'ENRICHED',timeline:[],allowedTransitions:['INDUSTRY_CONNECTED']}
const empty: Dashboard={byStatus:{},byCategory:{},byDistrict:{},open:0,resolved:0,rejected:0,universityParticipation:{},industryPledges:{},pledgesByType:{},fundingByCurrency:{},averageStageSeconds:{},stageSamples:{},uniqueIncidents:0}
function page<T>(content:T[],last=true): Page<T> {return {content,last,number:0,size:20,totalElements:content.length,totalPages:last ? 1 : 2}}
function change(label: string, value: string) {fireEvent.change(screen.getByLabelText(label),{target:{value}})}
function show(element: ReactNode,role: Role='INDUSTRY',path='/industry') {
  return render(<AuthContext.Provider value={{user:{id:'u1',name:'Reader',role},loading:false,login:async()=>{},logout:()=>{}}}><MemoryRouter initialEntries={[path]}><Routes><Route path="/industry" element={element}/><Route path="/government" element={element}/><Route path="/projects/:id" element={<ProjectWorkspace/>}/></Routes></MemoryRouter></AuthContext.Provider>)
}
function form(saved=vi.fn().mockResolvedValue(undefined)) {const pledgeCall=vi.spyOn(api,'pledge').mockResolvedValue(pledge);show(<PledgeForm projectId="p1" saved={saved}/>);return {pledgeCall,saved}}
function submitPledge() {fireEvent.submit(screen.getByText('Submit pledge').closest('form')!)}

describe('pledge form',() => {
  it('requires a description and shows only the requested support types',async() => {
    const {pledgeCall}=form();expect(screen.getAllByRole('option').map(o => o.textContent)).toEqual(['FUNDING','IN_KIND','MENTORSHIP'])
    change('Support description','   ');submitPledge();await screen.findByText('A support description is required.');expect(pledgeCall).not.toHaveBeenCalled()
  })
  it.each([['','INR'],['100',''],['0','INR'],['-1','INR'],['1.001','INR'],['10','inr'],['10','AB']])('rejects invalid funding amount %s and currency %s',async(amount,currency) => {
    const {pledgeCall}=form();expect(screen.getByLabelText('Amount')).toHaveProperty('required',true);expect(screen.getByLabelText('Currency')).toHaveProperty('required',true)
    change('Support description','Repair support');change('Amount',amount);change('Currency',currency);submitPledge()
    await screen.findByText(/Funding requires a positive amount/);expect(pledgeCall).not.toHaveBeenCalled()
  })
  it('submits funding and refreshes after success',async() => {
    const {pledgeCall,saved}=form();change('Support description',' Repair support ');change('Amount','123.45');change('Currency','INR');submitPledge()
    await screen.findByText('Pledge recorded.');expect(pledgeCall).toHaveBeenCalledWith('p1',{supportType:'FUNDING',amount:123.45,currency:'INR',description:'Repair support'});expect(saved).toHaveBeenCalledOnce();expect(screen.getByLabelText('Amount')).toHaveProperty('value','')
  })
  it.each(['IN_KIND','MENTORSHIP'])('omits funding fields after switching to %s',async supportType => {
    const {pledgeCall}=form();change('Amount','100');change('Currency','INR');change('Support type',supportType);expect(screen.queryByLabelText('Amount')).toBeNull();change('Support description','Research support');submitPledge()
    await screen.findByText('Pledge recorded.');expect(pledgeCall).toHaveBeenCalledWith('p1',{supportType,description:'Research support'})
  })
  it('preserves fields on backend errors and distinguishes post-save refresh errors',async() => {
    const saved=vi.fn().mockRejectedValue(new Error('Offline'));const {pledgeCall}=form(saved);pledgeCall.mockRejectedValueOnce(new ApiError(409,{message:'Project is unavailable'}))
    change('Support type','MENTORSHIP');change('Support description','Research support');submitPledge();await screen.findByText('Project is unavailable');expect(screen.getByLabelText('Support description')).toHaveProperty('value','Research support')
    submitPledge();await screen.findByText(/Pledge recorded, but the project could not be refreshed/);expect(pledgeCall).toHaveBeenCalledTimes(2);expect(screen.getByLabelText('Support description')).toHaveProperty('value','')
  })
})
describe('industry pages',() => {
  it('separates first-pledge and already-supported projects with details and own pledges',async() => {
    vi.spyOn(api,'industryProjects').mockResolvedValue(page([project,{...project,id:'p2',title:'Supported project',status:'INDUSTRY_CONNECTED'}]));vi.spyOn(api,'industryPledges').mockResolvedValue(page([pledge]));show(<IndustryDashboard/>)
    const ready=await screen.findByRole('link',{name:'Water project'});expect(ready.getAttribute('href')).toBe('/projects/p1');expect(screen.getByRole('link',{name:'Supported project'}).getAttribute('href')).toBe('/projects/p2')
    const table=await screen.findByRole('table',{name:'Own pledges'});expect(within(table).getByText('Repair support')).toBeTruthy();expect(within(table).getByText('INR')).toBeTruthy();expect(within(table).getByRole('link').getAttribute('href')).toBe('/projects/p1')
  })
  it('paginates projects and pledges independently',async() => {
    const projects=vi.spyOn(api,'industryProjects').mockResolvedValue(page([project],false));const pledges=vi.spyOn(api,'industryPledges').mockResolvedValue(page([pledge],false));show(<IndustryDashboard/>);await screen.findByText('Next projects');await screen.findByText('Next pledges')
    fireEvent.click(screen.getByText('Next projects'));await waitFor(() => expect(projects).toHaveBeenLastCalledWith({page:1,size:20}));expect(pledges).toHaveBeenCalledOnce()
    fireEvent.click(screen.getByText('Next pledges'));await waitFor(() => expect(pledges).toHaveBeenLastCalledWith({page:1,size:20}))
  })
  it('shows independent load errors and retries',async() => {
    const projects=vi.spyOn(api,'industryProjects').mockRejectedValueOnce(new ApiError(503,{message:'Discovery unavailable'})).mockResolvedValue(page([]));vi.spyOn(api,'industryPledges').mockResolvedValue(page([]));show(<IndustryDashboard/>);await screen.findByText('Discovery unavailable');await screen.findByText('No pledges.')
    fireEvent.click(screen.getByText('Refresh projects'));await screen.findByText('0 projects available for support',{exact:false});expect(projects).toHaveBeenCalledTimes(2)
  })
  it('shows industry-facing project fields, permits additional pledges and refreshes status',async() => {
    const load=vi.spyOn(api,'project').mockResolvedValue({...project,status:'INDUSTRY_CONNECTED'});vi.spyOn(api,'challenge').mockResolvedValue({...detail,status:'INDUSTRY_CONNECTED',allowedTransitions:[]});const post=vi.spyOn(api,'pledge').mockResolvedValue(pledge)
    show(<IndustryDashboard/>,'INDUSTRY','/projects/p1');await screen.findByText('Safer water',{exact:false});expect(screen.getByText('Survey completed',{exact:false})).toBeTruthy();expect(screen.queryByRole('heading',{name:'Proposal'})).toBeNull();expect(screen.queryByText('Save team')).toBeNull()
    change('Support type','MENTORSHIP');change('Support description','Research support');submitPledge();await screen.findByText('Pledge recorded.');expect(post).toHaveBeenCalledWith('p1',{supportType:'MENTORSHIP',description:'Research support'});await waitFor(() => expect(load).toHaveBeenCalledTimes(2))
  })
})
describe('government analytics',() => {
  it('renders actual Recharts status and category charts with empty data',async() => {
    vi.spyOn(api,'analytics').mockResolvedValue(empty);const {container}=show(<GovernmentAnalytics/>,'GOVERNMENT','/government')
    await screen.findByRole('figure',{name:'Reports by status chart'});expect(screen.getByRole('figure',{name:'Reports by category chart'})).toBeTruthy();expect(screen.getAllByText('No chart data.')).toHaveLength(2)
    await waitFor(() => expect(container.querySelectorAll('svg.recharts-surface')).toHaveLength(2));expect(screen.getByText('No completed stage visits.')).toBeTruthy()
  })
  it('renders charts with zero-valued data',() => {
    const {container}=render(<CountChart title="Zero chart" counts={{SUBMITTED:0,VALIDATED:0}}/>);expect(screen.getByRole('figure',{name:'Zero chart'})).toBeTruthy();expect(container.querySelector('svg')).toBeTruthy()
  })
  it('renders counts, sorted districts, global unique incidents and outcome samples',async() => {
    vi.spyOn(api,'analytics').mockResolvedValue({...empty,byStatus:{SUBMITTED:3,DEPLOYED:2},byCategory:{HEALTH:5},byDistrict:{Gumla:1,Ranchi:4},uniqueIncidents:4,open:3,resolved:2,universityParticipation:{org1:2},industryPledges:{i1:1},pledgesByType:{FUNDING:1},fundingByCurrency:{INR:100},averageStageSeconds:{SUBMITTED:12.5},stageSamples:{SUBMITTED:2}})
    show(<GovernmentAnalytics/>,'GOVERNMENT','/government');const districts=await screen.findByRole('table',{name:'Districts by report count'});const rows=within(districts).getAllByRole('row');expect(rows[1].textContent).toBe('Ranchi4');expect(rows[2].textContent).toBe('Gumla1')
    expect(within(districts).getByRole('link',{name:'Ranchi'}).getAttribute('href')).toBe('/explore?district=Ranchi');expect(screen.getByText(/Unique incidents across all districts: 4/)).toBeTruthy()
    const totals=screen.getByRole('table',{name:'Report totals and outcomes'});expect(within(totals).getByText('Unique incidents').closest('tr')?.textContent).toBe('Unique incidents4');expect(within(totals).getByText('Total reports').closest('tr')?.textContent).toBe('Total reports5')
    expect(screen.getByRole('table',{name:'Completed lifecycle stage visits'}).textContent).toContain('SUBMITTED12.52');expect(screen.getByRole('table',{name:'Funding by currency'}).textContent).toContain('INR100')
    expect(screen.getByRole('link',{name:'org1'}).getAttribute('href')).toBe('/universities/org1/departments')
  })
  it('retries analytics failures and resolves project drill-down links',async() => {
    const analytics=vi.spyOn(api,'analytics').mockRejectedValueOnce(new ApiError(503,{message:'Analytics unavailable'})).mockResolvedValue(empty);const lookup=vi.spyOn(api,'project').mockResolvedValue(project);show(<GovernmentAnalytics/>,'GOVERNMENT','/government')
    await screen.findByText('Analytics unavailable');fireEvent.click(screen.getByText('Refresh analytics'));await screen.findByText('Unique incidents');expect(analytics).toHaveBeenCalledTimes(2)
    change('Project ID',' p1 ');fireEvent.click(screen.getByRole('button',{name:'Find project'}));expect((await screen.findByRole('link',{name:'Open Water project'})).getAttribute('href')).toBe('/projects/p1');expect(lookup).toHaveBeenCalledWith('p1')
  })
  it.each(['PILOT','DEPLOYED','IMPACT_EVALUATED'] as Status[])('runs only the allowed %s action from a project page',async target => {
    const load=vi.spyOn(api,'project').mockResolvedValue({...project,status:'PROTOTYPE',proposalText:'Research proposal'});const challenge=vi.spyOn(api,'challenge').mockResolvedValue({...detail,allowedTransitions:[target]});const transition=vi.spyOn(api,'transition').mockResolvedValue({...detail,status:target})
    show(<GovernmentAnalytics/>,'GOVERNMENT','/projects/p1');fireEvent.click(await screen.findByText(`Move to ${target}`));expect(screen.getAllByRole('button',{name:/Move to/})).toHaveLength(1);expect(screen.queryByText('Submit pledge')).toBeNull()
    if(target==='IMPACT_EVALUATED') change('Impact summary','Measured safer water');else change('Reason (required)','Verified field evidence')
    challenge.mockResolvedValue({...detail,status:target,allowedTransitions:[]});load.mockResolvedValue({...project,status:target});fireEvent.click(screen.getByText('Submit action'));await screen.findByText('No lifecycle actions available.')
    expect(transition).toHaveBeenCalledWith('c1',expect.objectContaining(target==='IMPACT_EVALUATED' ? {toState:target,impactSummary:'Measured safer water'} : {toState:target,reason:'Verified field evidence'}))
  })
  it('displays backend transition conflicts',async() => {
    vi.spyOn(api,'project').mockResolvedValue(project);vi.spyOn(api,'challenge').mockResolvedValue({...detail,allowedTransitions:['PILOT']});vi.spyOn(api,'transition').mockRejectedValue(new ApiError(409,{message:'Project state changed'}));show(<GovernmentAnalytics/>,'GOVERNMENT','/projects/p1')
    fireEvent.click(await screen.findByText('Move to PILOT'));change('Reason (required)','Verified field evidence');fireEvent.click(screen.getByText('Submit action'));await screen.findByText('Project state changed')
  })
})

describe('19a error and null contracts',()=>{
  it('preserves the backend message when refresh fails after a successful pledge',async()=>{
    form(vi.fn().mockRejectedValue(new ApiError(403,{code:'FORBIDDEN',message:'Organization access denied'})))
    change('Support type','MENTORSHIP');change('Support description','Expert help');submitPledge()
    expect((await screen.findByRole('alert')).textContent).toContain('Organization access denied')
    expect(screen.getByRole('status').textContent).toContain('Pledge recorded, but the project could not be refreshed')
  })
  it('renders empty discovery and own pledge pages',async()=>{
    vi.spyOn(api,'industryProjects').mockResolvedValue(page([]));vi.spyOn(api,'industryPledges').mockResolvedValue(page([]))
    show(<IndustryDashboard/>);await screen.findByText('No pledges.');expect(screen.getAllByText('No projects on this page.')).toHaveLength(2)
  })
  it('renders a project with nullable proposal, impact, team and milestone outcome/completion',async()=>{
    vi.spyOn(api,'project').mockResolvedValue({...project,proposalText:null,impactSummary:null,team:null,milestones:[{...project.milestones[0],status:'PENDING',outcome:null,completedAt:null}]})
    vi.spyOn(api,'challenge').mockResolvedValue({...detail,allowedTransitions:[]})
    show(<ProjectWorkspace/>,'GOVERNMENT','/projects/p1');await screen.findByText('No team information.')
    expect(screen.getByText('Impact: Not recorded')).toBeTruthy();expect(screen.getByText('Outcome: Not recorded')).toBeTruthy()
  })
  it('renders non-funding pledge fields that are null',async()=>{
    vi.spyOn(api,'industryProjects').mockResolvedValue(page([]));vi.spyOn(api,'industryPledges').mockResolvedValue(page([{...pledge,supportType:'MENTORSHIP',amount:null,currency:null}]))
    show(<IndustryDashboard/>);await screen.findByText('MENTORSHIP');expect(screen.getAllByText('—')).toHaveLength(2)
  })
})

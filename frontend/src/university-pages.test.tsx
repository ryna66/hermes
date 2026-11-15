import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { api, ApiError } from './api'
import { AuthContext } from './auth-context'
import { DepartmentProfile, ProjectWorkspace, UniversityInbox } from './university-pages'
import type { Detail, Inbox, Page, Project, Role } from './types'
afterEach(() => vi.restoreAllMocks())
const item: Inbox={id:'c1',title:'Water report',description:'Repair borewell',status:'ASSIGNED',category:'WATER_SANITATION',assignmentResponse:'PENDING',responseDeadline:'2026-11-20T00:00:00Z',overdue:false,projectId:null}
const project: Project={id:'p1',challengeId:'c1',universityId:'org1',title:'Water project',proposalText:'Survey and repair',impactSummary:null,status:'INDUSTRY_CONNECTED',team:{id:'t1',name:'Water team',members:[]},milestones:[]}
const detail: Detail={...item,district:'Gumla',latitude:23,longitude:84,affectedPeople:12,hasPhoto:false,hasAudio:false,fixedConfirmedAt:null,createdAt:'2026-11-01T00:00:00Z',aiStatus:'ENRICHED',timeline:[],allowedTransitions:['PROTOTYPE']}
function page<T>(content:T[],last=true): Page<T> {return {content,last,number:0,size:20,totalElements:content.length,totalPages:last ? 1 : 2}}
function show(path='/university/inbox',role: Role | null='UNIVERSITY') {
  render(<AuthContext.Provider value={{user:role ? {id:'u1',name:'University user',role} : null,loading:false,login:async()=>{},logout:()=>{}}}><MemoryRouter initialEntries={[path]}><Routes><Route path="/university/inbox" element={<UniversityInbox/>}/><Route path="/universities/:id/departments" element={<DepartmentProfile/>}/><Route path="/projects/:id" element={<ProjectWorkspace/>}/></Routes></MemoryRouter></AuthContext.Provider>)
}
function change(label: string, value: string) {fireEvent.change(screen.getByLabelText(label),{target:{value}})}
function workspace(role: Role='UNIVERSITY',p=project) {vi.spyOn(api,'project').mockResolvedValue(p);vi.spyOn(api,'challenge').mockResolvedValue(detail);show('/projects/p1',role)}
describe('university inbox',() => {
  it('requires a nonblank decline reason and refreshes after declining',async() => {
    const inbox=vi.spyOn(api,'inbox').mockResolvedValue(page([item]));const respond=vi.spyOn(api,'respond').mockResolvedValue(detail);show()
    await screen.findByText('Decline assignment');const form=screen.getByText('Decline assignment').closest('form')!
    expect(screen.getByLabelText('Decline reason')).toHaveProperty('required',true)
    fireEvent.submit(form);await screen.findByText('A decline reason is required.');expect(respond).not.toHaveBeenCalled()
    change('Decline reason','   ');fireEvent.submit(form);expect(respond).not.toHaveBeenCalled()
    inbox.mockResolvedValue(page([]));change('Decline reason',' Outside expertise ');fireEvent.submit(form)
    await screen.findByText('No assignments.');expect(respond).toHaveBeenCalledWith('c1','DECLINED','Outside expertise');expect(inbox).toHaveBeenCalledTimes(2)
  })
  it('accepts assignments and exposes proposal creation',async() => {
    const inbox=vi.spyOn(api,'inbox').mockResolvedValue(page([item]));const respond=vi.spyOn(api,'respond').mockResolvedValue(detail);show();await screen.findByText('Accept assignment')
    inbox.mockResolvedValue(page([{...item,assignmentResponse:'ACCEPTED'}]));fireEvent.click(screen.getByText('Accept assignment'))
    await screen.findByText('Create project');expect(respond).toHaveBeenCalledWith('c1','ACCEPTED',undefined)
  })
  it('sends response filters and pagination and marks overdue assignments',async() => {
    const inbox=vi.spyOn(api,'inbox').mockResolvedValue(page([{...item,overdue:true}],false));show()
    await screen.findByText('Overdue');expect(screen.getByText('Accept assignment').closest('fieldset')).toHaveProperty('disabled',true)
    expect(screen.getByText(/Response deadline:/).querySelector('time')?.dateTime).toBe(item.responseDeadline)
    fireEvent.click(screen.getByText('Next'));await waitFor(() => expect(inbox).toHaveBeenLastCalledWith({assignmentResponse:'PENDING',page:1,size:20}))
    for (const response of ['ACCEPTED','DECLINED']) {change('Assignment response',response);await waitFor(() => expect(inbox).toHaveBeenLastCalledWith({assignmentResponse:response,page:0,size:20}))}
  })
  it.each(['GENERATED','TEMPLATE_FALLBACK'])('labels a %s proposal as a draft even if the draft flag is false',async aiStatus => {
    vi.spyOn(api,'inbox').mockResolvedValue(page([{...item,assignmentResponse:'ACCEPTED'}]));const draft=vi.spyOn(api,'draft').mockResolvedValue({proposal:'Generated survey proposal',draft:false,aiStatus});const create=vi.spyOn(api,'createProject').mockResolvedValue(project)
    vi.spyOn(api,'project').mockResolvedValue(project);vi.spyOn(api,'challenge').mockResolvedValue(detail);show()
    fireEvent.click(await screen.findByText('Generate draft'));await screen.findByText(/DRAFT — the team must review and edit/)
    expect(screen.getByLabelText('Proposal text')).toHaveProperty('value','Generated survey proposal');expect(draft).toHaveBeenCalledWith('c1')
    change('Project title',' Water project ');change('Team name',' Water team ');change('Proposal text','Reviewed survey proposal');fireEvent.click(screen.getByText('Create project'))
    await screen.findByRole('heading',{name:'Project workspace'});expect(create).toHaveBeenCalledWith('c1',{title:'Water project',teamName:'Water team',proposalText:'Reviewed survey proposal'})
  })
  it('preserves typed proposal on draft failure',async() => {
    vi.spyOn(api,'inbox').mockResolvedValue(page([{...item,assignmentResponse:'ACCEPTED'}]));vi.spyOn(api,'draft').mockRejectedValue(new ApiError(409,{message:'Accepted assignment required'}));show()
    await screen.findByText('Generate draft');change('Proposal text','Team text');fireEvent.click(screen.getByText('Generate draft'));await screen.findByText('Accepted assignment required');expect(screen.getByLabelText('Proposal text')).toHaveProperty('value','Team text')
  })
  it('shows backend acceptance errors',async() => {
    vi.spyOn(api,'inbox').mockResolvedValue(page([item]));vi.spyOn(api,'respond').mockRejectedValue(new ApiError(409,{message:'Response deadline has passed'}));show()
    fireEvent.click(await screen.findByText('Accept assignment'));await screen.findByText('Response deadline has passed')
  })
})
describe('department profiles',() => {
  const faculty=[{id:'f1',name:'Dr Water',expertiseTags:['water'],bio:'Researcher'},{id:'f2',name:'Dr Health',expertiseTags:['health'],bio:'Teacher'}]
  it('shows public grouped faculty without editing controls',async() => {
    const departments=vi.spyOn(api,'departments').mockResolvedValue({Engineering:faculty});show('/universities/org1/departments',null)
    await screen.findByText('Dr Water');expect(departments).toHaveBeenCalledWith('org1');expect(screen.queryByText('Edit my Engineering faculty')).toBeNull()
  })
  it('displays the 409 referenced-faculty message and retains the editor',async() => {
    vi.spyOn(api,'departments').mockResolvedValue({Engineering:faculty});const replace=vi.spyOn(api,'replaceFaculty').mockRejectedValue(new ApiError(409,{code:'CONFLICT',message:'Referenced faculty cannot be deleted: Dr Water'}));show('/universities/org1/departments')
    fireEvent.click(await screen.findByText('Edit my Engineering faculty'));fireEvent.click(screen.getByText('Remove Dr Water'));fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(screen.getByText('Save faculty'))
    await screen.findByText('Referenced faculty cannot be deleted: Dr Water');expect(screen.getByText('CONFLICT (409)')).toBeTruthy();expect(screen.getByText('Save faculty')).toBeTruthy();expect(replace).toHaveBeenCalledWith('Engineering',[faculty[1]])
  })
  it('preserves IDs, saves new faculty and refreshes',async() => {
    const departments=vi.spyOn(api,'departments').mockResolvedValue({Engineering:[faculty[0]]});const replace=vi.spyOn(api,'replaceFaculty').mockResolvedValue(faculty);show('/universities/org1/departments')
    fireEvent.click(await screen.findByText('Edit my Engineering faculty'));fireEvent.click(screen.getByText('Add faculty'));fireEvent.change(screen.getAllByLabelText('Name')[1],{target:{value:' New Faculty '}});fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(screen.getByText('Save faculty'))
    await screen.findByText('Faculty saved.');expect(replace).toHaveBeenCalledWith('Engineering',[faculty[0],{name:'New Faculty',expertiseTags:[],bio:''}]);await waitFor(() => expect(departments).toHaveBeenCalledTimes(2))
  })
})
describe('project workspace',() => {
  it('saves proposal edits and explains unsupported regeneration',async() => {
    const edit=vi.spyOn(api,'editProject').mockResolvedValue({...project,proposalText:'Reviewed plan'});workspace();await screen.findByText('Save proposal')
    expect(screen.getByText('Generate draft')).toHaveProperty('disabled',true);expect(screen.getByText(/API does not support regenerating/)).toBeTruthy()
    change('Proposal text',' Reviewed plan ');change('Impact summary','Served village');fireEvent.click(screen.getByText('Save proposal'));await screen.findByText('Proposal saved.')
    expect(edit).toHaveBeenCalledWith('p1',{proposalText:'Reviewed plan',impactSummary:'Served village'})
  })
  it('replaces team using faculty IDs and roles',async() => {
    const team=vi.spyOn(api,'team').mockResolvedValue(project);workspace();fireEvent.click(await screen.findByText('Add team member'));change('Faculty profile ID','f1');change('Team role','LEAD');fireEvent.click(screen.getByText('Save team'))
    await screen.findByText('Team saved.');expect(team).toHaveBeenCalledWith('p1',{name:'Water team',members:[{facultyProfileId:'f1',role:'LEAD'}]})
  })
  it('adds and updates milestone progress and outcome',async() => {
    const milestone={id:'m1',title:'Survey',description:'Visit site',dueDate:'2026-12-01',status:'PENDING' as const,outcome:null,completedAt:null}
    const add=vi.spyOn(api,'addMilestone').mockResolvedValue(milestone);const edit=vi.spyOn(api,'editMilestone').mockResolvedValue({...milestone,status:'COMPLETED',outcome:'Prototype evidence'})
    workspace();await screen.findByText('Save proposal');change('Milestone title','Survey');change('Description','Visit site');change('Due date','2026-12-01')
    vi.mocked(api.project).mockResolvedValue({...project,milestones:[milestone]});fireEvent.click(screen.getByRole('button',{name:'Add milestone'}));await screen.findByRole('heading',{name:'Update Survey'})
    expect(add).toHaveBeenCalledWith('p1',{title:'Survey',description:'Visit site',dueDate:'2026-12-01',status:'PENDING',outcome:null})
    const form=screen.getByRole('heading',{name:'Update Survey'}).closest('form')!;const update=within(form)
    fireEvent.change(update.getByLabelText('Progress'),{target:{value:'COMPLETED'}});fireEvent.submit(form);await update.findByText(/completed milestones require an outcome/);expect(edit).not.toHaveBeenCalled()
    fireEvent.change(update.getByLabelText('Outcome'),{target:{value:'Prototype evidence'}});fireEvent.click(update.getByText('Update milestone'))
    await waitFor(() => expect(edit).toHaveBeenCalledWith('p1','m1',{title:'Survey',description:'Visit site',dueDate:'2026-12-01',status:'COMPLETED',outcome:'Prototype evidence'}))
  })
  it('uses API allowedTransitions and refreshes after transition',async() => {
    const transition=vi.spyOn(api,'transition').mockResolvedValue(detail);workspace();fireEvent.click(await screen.findByText('Move to PROTOTYPE'))
    expect(screen.queryByText('Move to PILOT')).toBeNull();vi.mocked(api.challenge).mockResolvedValue({...detail,status:'PROTOTYPE',allowedTransitions:[]});fireEvent.click(screen.getByText('Submit action'))
    await screen.findByText('No lifecycle actions available.');expect(transition).toHaveBeenCalledWith('c1',expect.objectContaining({toState:'PROTOTYPE'}));expect(api.project).toHaveBeenCalledTimes(2)
  })
  it('keeps project readable if lifecycle lookup fails',async() => {
    vi.spyOn(api,'project').mockResolvedValue(project);vi.spyOn(api,'challenge').mockRejectedValue(new ApiError(503,{message:'Lifecycle unavailable'}));show('/projects/p1')
    await screen.findByText('Lifecycle unavailable');expect(screen.getByText('Save proposal')).toBeTruthy();expect(screen.queryByText('Move to PROTOTYPE')).toBeNull()
  })
  it.each(['ADMIN','GOVERNMENT','INDUSTRY'] as const)('hides university editing for %s readers',async role => {
    workspace(role);await screen.findByText('Water project');expect(screen.queryByText('Save proposal')).toBeNull();expect(screen.queryByText('Save team')).toBeNull();expect(screen.queryByRole('button',{name:'Add milestone'})).toBeNull()
  })
})

describe('19a empty and nullable university contracts',()=>{
  it('renders an empty inbox',async()=>{vi.spyOn(api,'inbox').mockResolvedValue(page([]));show();await screen.findByText('No assignments.')})
  it('renders empty public departments',async()=>{vi.spyOn(api,'departments').mockResolvedValue({});show('/universities/org1/departments',null);await screen.findByText('No departments.')})
  it('renders a nullable category, deadline and project link',async()=>{
    vi.spyOn(api,'inbox').mockResolvedValue(page([{...item,category:null,responseDeadline:null,projectId:null}]))
    show();await screen.findByText('ASSIGNED — PENDING — Uncategorized');expect(screen.getByText('Response deadline: None')).toBeTruthy()
  })
})

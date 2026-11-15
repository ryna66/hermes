import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from './api'
import { useAuth } from './auth-context'
import { ErrorDisplay, Loading } from './components'
import { ActionForm } from './ChallengeDetail'
import PledgeForm from './PledgeForm'
import type { AssignmentResponse, Detail, Faculty, FacultyInput, Inbox, Page, Progress, ProgressView, Project, Status, TeamInput } from './types'

function value(form: FormData, key: string) { return String(form.get(key) ?? '').trim() }
function notify() { window.dispatchEvent(new Event('notifications-changed')) }

function ProposalEditor({challengeId, project, created, saved}: {challengeId: string; project?: Project; created?: (project: Project) => void; saved?: (project: Project) => void}) {
  const [proposal,setProposal] = useState(project?.proposalText ?? '')
  const [draftStatus,setDraftStatus] = useState<string | null>(null)
  const [busy,setBusy] = useState(false); const [error,setError] = useState<unknown>(null); const [message,setMessage] = useState('')
  async function generate() {
    setBusy(true); setError(null); setMessage('')
    try { const result=await api.draft(challengeId); setProposal(result.proposal); setDraftStatus(result.aiStatus) }
    catch(e) { setError(e) } finally { setBusy(false) }
  }
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null); setMessage('')
    const form=new FormData(e.currentTarget)
    if (!proposal.trim() || (!project && (!value(form,'title') || !value(form,'teamName')))) {setError(new Error('Project title, proposal and team name must not be blank.')); return}
    setBusy(true)
    try {
      if (project) { const result=await api.editProject(project.id,{proposalText:proposal.trim(),impactSummary:value(form,'impactSummary')}); saved?.(result); setMessage('Proposal saved.') }
      else { const result=await api.createProject(challengeId,{title:value(form,'title'),proposalText:proposal.trim(),teamName:value(form,'teamName')}); created?.(result) }
      notify()
    } catch(e) { setError(e) } finally { setBusy(false) }
  }
  return <section><h3>Proposal editor</h3><ErrorDisplay error={error}/>{message && <p role="status">{message}</p>}<form onSubmit={submit}><fieldset disabled={busy}><legend>Proposal</legend>
    {!project && <><label>Project title <input name="title" required maxLength={255}/></label><label>Team name <input name="teamName" required maxLength={255}/></label></>}
    <button type="button" disabled={!!project} onClick={() => void generate()}>Generate draft</button>
    {project && <p>Draft generation is available in the inbox after accepting an assignment and before creating its project. The API does not support regenerating a draft for an existing project.</p>}
    {draftStatus !== null && <p role="status">DRAFT — the team must review and edit this generated proposal before submission. AI status: {draftStatus}</p>}
    <label>Proposal text <textarea required value={proposal} onChange={e => setProposal(e.target.value)}/></label>
    {project && <label>Impact summary <textarea name="impactSummary" defaultValue={project.impactSummary ?? ''}/></label>}
    <button type="submit">{project ? 'Save proposal' : 'Create project'}</button></fieldset></form>{busy && <Loading/>}</section>
}

function InboxItem({item, refresh}: {item: Inbox; refresh: () => Promise<void>}) {
  const navigate=useNavigate(); const [reason,setReason]=useState(''); const [busy,setBusy]=useState(false); const [error,setError]=useState<unknown>(null); const [message,setMessage]=useState('')
  async function respond(response: AssignmentResponse) {
    setError(null); setMessage('')
    if (response === 'DECLINED' && !reason.trim()) {setError(new Error('A decline reason is required.')); return}
    setBusy(true)
    try {await api.respond(item.id,response,response === 'DECLINED' ? reason.trim() : undefined); setMessage(`Assignment ${response.toLowerCase()}.`); await refresh(); notify()}
    catch(e) {setError(e)} finally {setBusy(false)}
  }
  return <article><h2><Link to={`/challenges/${item.id}`}>{item.title}</Link></h2><p>{item.description}</p><p>{item.status} — {item.assignmentResponse} — {item.category ?? 'Uncategorized'}</p>
    <p>Response deadline: {item.responseDeadline ? <time dateTime={item.responseDeadline}>{new Date(item.responseDeadline).toLocaleString()}</time> : 'None'} {item.overdue && <strong>Overdue</strong>}</p>
    <ErrorDisplay error={error}/>{message && <p role="status">{message}</p>}
    {item.status === 'ASSIGNED' && item.assignmentResponse === 'PENDING' && <fieldset disabled={busy || item.overdue}><legend>Assignment response</legend><button onClick={() => void respond('ACCEPTED')}>Accept assignment</button><form onSubmit={e => {e.preventDefault(); void respond('DECLINED')}}><label>Decline reason <textarea required value={reason} onChange={e => setReason(e.target.value)}/></label><button type="submit">Decline assignment</button></form></fieldset>}
    {item.projectId ? <Link to={`/projects/${item.projectId}`}>Open project workspace</Link> : item.assignmentResponse === 'ACCEPTED' && item.status === 'ASSIGNED' && <ProposalEditor challengeId={item.id} created={p => navigate(`/projects/${p.id}`)}/>}
  </article>
}

export function UniversityInbox() {
  const [filter,setFilter]=useState<AssignmentResponse | ''>('PENDING'); const [page,setPage]=useState(0); const [result,setResult]=useState<Page<Inbox> | null>(null)
  const [error,setError]=useState<unknown>(null); const [loading,setLoading]=useState(true); const [reload,setReload]=useState(0)
  useEffect(() => {
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
    let active=true; setLoading(true); setError(null)
    api.inbox({assignmentResponse:filter || undefined,page,size:20}).then(r => {if(active)setResult(r)}).catch(e => {if(active)setError(e)}).finally(() => {if(active)setLoading(false)})
    return () => {active=false}
  },[filter,page,reload])
  async function refresh() {
    const r=await api.inbox({assignmentResponse:filter || undefined,page,size:20})
    if (!r.content.length && page>0) setPage(p => p-1)
    else setResult(r)
  }
  return <><h1>University inbox</h1><OrganizationLookup/><label>Assignment response <select value={filter} onChange={e => {setFilter(e.target.value as AssignmentResponse | '');setPage(0);setResult(null)}}><option value="">All</option>{['PENDING','ACCEPTED','DECLINED'].map(r => <option key={r}>{r}</option>)}</select></label><button onClick={() => setReload(n => n+1)}>Refresh inbox</button><ErrorDisplay error={error}/>{loading ? <Loading/> : !error && result && <><p>Page {result.number+1}; {result.totalElements} assignments</p>{!result.content.length && <p>No assignments.</p>}{result.content.map(item => <InboxItem key={`${item.id}:${item.assignmentResponse}`} item={item} refresh={refresh}/>)}<button disabled={page===0} onClick={() => setPage(p => p-1)}>Previous</button><button disabled={result.last} onClick={() => setPage(p => p+1)}>Next</button></>}</>
}
function OrganizationLookup() {
  const navigate=useNavigate()
  return <form onSubmit={e => {e.preventDefault();navigate(`/universities/${encodeURIComponent(value(new FormData(e.currentTarget),'organization'))}/departments`)}}><label>University organization ID <input name="organization" required/></label><button>View departments</button></form>
}

function FacultyEditor({department, initial, saved, cancel}: {department: string; initial: Faculty[]; saved: () => void; cancel: () => void}) {
  const [rows,setRows]=useState<FacultyInput[]>(initial.map(f => ({...f}))); const [error,setError]=useState<unknown>(null); const [busy,setBusy]=useState(false)
  const update=(index: number, patch: Partial<FacultyInput>) => setRows(rows.map((r,i) => i===index ? {...r,...patch} : r))
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null)
    if (rows.some(r => !r.name.trim() || r.expertiseTags.some(t => !t.trim()))) {setError(new Error('Faculty names and expertise tags must not be blank.'));return}
    setBusy(true)
    try {await api.replaceFaculty(department,rows.map(r => ({...r,name:r.name.trim(),expertiseTags:r.expertiseTags.map(t => t.trim())}))); saved()}
    catch(e) {setError(e)} finally {setBusy(false)}
  }
  return <form onSubmit={submit}><h3>Edit {department}</h3><p>This replaces the entire department faculty list in your signed-in university. Omitted profiles are removed. Referenced profiles cannot be removed.</p><ErrorDisplay error={error}/><fieldset disabled={busy}><legend>Faculty profiles</legend>{rows.map((r,i) => <fieldset key={i}><legend>Faculty {i+1}</legend>{r.id && <p>Faculty ID: {r.id}</p>}<label>Name <input required maxLength={200} value={r.name} onChange={e => update(i,{name:e.target.value})}/></label><label>Expertise tags (comma separated) <input value={r.expertiseTags.join(',')} onChange={e => update(i,{expertiseTags:e.target.value ? e.target.value.split(',') : []})}/></label><label>Biography <textarea required={false} maxLength={10000} value={r.bio} onChange={e => update(i,{bio:e.target.value})}/></label><button type="button" onClick={() => setRows(rows.filter((_,n) => n!==i))}>Remove {r.name || `faculty ${i+1}`}</button></fieldset>)}<button type="button" onClick={() => setRows([...rows,{name:'',expertiseTags:[],bio:''}])}>Add faculty</button><label><input type="checkbox" required/>I confirm the displayed organization is my university and this is its complete faculty list for this department.</label><button type="submit">Save faculty</button><button type="button" onClick={cancel}>Cancel</button></fieldset></form>
}
export function DepartmentProfile() {
  const {id=''}=useParams(); const {user}=useAuth()
  return <DepartmentContent key={`${id}:${user?.id ?? 'public'}`} id={id}/>
}
function DepartmentContent({id}: {id: string}) {
  const {user}=useAuth(); const [departments,setDepartments]=useState<Record<string,Faculty[]> | null>(null); const [error,setError]=useState<unknown>(null); const [edit,setEdit]=useState<string | null>(null); const [reload,setReload]=useState(0); const [message,setMessage]=useState('')
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
  useEffect(() => {let active=true;setDepartments(null);setError(null);api.departments(id).then(d => {if(active)setDepartments(d)}).catch(e => {if(active)setError(e)});return () => {active=false}},[id,reload])
  return <><h1>University department profile</h1><p>Organization: {id}</p><OrganizationLookup/><ErrorDisplay error={error}/>{message && <p role="status">{message}</p>}{!departments ? error ? <button onClick={() => setReload(n => n+1)}>Retry departments</button> : <Loading/> : <>{!Object.keys(departments).length && <p>No departments.</p>}{user?.role==='UNIVERSITY' && <p>Your account does not expose its organization ID. Edit only your own university here; faculty updates always target the signed-in university and the server checks existing faculty ownership.</p>}{Object.entries(departments).map(([name,faculty]) => <section key={name}><h2>{name}</h2>{faculty.map(f => <article key={f.id}><h3>{f.name}</h3><p>Faculty ID: {f.id}</p><p>{f.expertiseTags.join(', ')}</p><p>{f.bio}</p></article>)}{user?.role==='UNIVERSITY' && edit===null && <button onClick={() => {setEdit(name);setMessage('')}}>Edit my {name} faculty</button>}{edit===name && <FacultyEditor department={name} initial={faculty} saved={() => {setEdit(null);setMessage('Faculty saved.');setReload(n => n+1)}} cancel={() => setEdit(null)}/>}</section>)}{user?.role==='UNIVERSITY' && edit===null && <form onSubmit={e => {e.preventDefault();const name=value(new FormData(e.currentTarget),'department');if(name) {setDepartments({...departments,[name]:departments[name] ?? []});setEdit(name);setMessage('')}}}><label>New department name <input name="department" required maxLength={200}/></label><button>Add department faculty</button></form>}</>}</>
}

function TeamEditor({project, saved}: {project: Project; saved: (p: Project) => void}) {
  const [name,setName]=useState(project.team?.name ?? ''); const [members,setMembers]=useState<TeamInput['members']>(project.team?.members.map(m => ({facultyProfileId:m.facultyProfileId,role:m.role})) ?? [])
  const [error,setError]=useState<unknown>(null); const [busy,setBusy]=useState(false); const [message,setMessage]=useState('')
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();setError(null);setMessage('')
    if (!name.trim() || members.some(m => !m.facultyProfileId.trim()) || new Set(members.map(m => m.facultyProfileId.trim())).size!==members.length || members.filter(m => m.role==='LEAD').length>1) {setError(new Error('Enter a team name, unique faculty IDs and at most one lead.'));return}
    setBusy(true)
    try {saved(await api.team(project.id,{name:name.trim(),members:members.map(m => ({...m,facultyProfileId:m.facultyProfileId.trim()}))}));setMessage('Team saved.');notify()} catch(e) {setError(e)} finally {setBusy(false)}
  }
  return <section><h2>Team editor</h2><p>Use faculty IDs from <Link to={`/universities/${project.universityId}/departments`}>your university departments</Link>.</p><ErrorDisplay error={error}/>{message && <p role="status">{message}</p>}<form onSubmit={submit}><fieldset disabled={busy}><legend>Team</legend><label>Team name <input required maxLength={255} value={name} onChange={e => setName(e.target.value)}/></label>{members.map((m,i) => <fieldset key={i}><legend>Member {i+1}</legend><label>Faculty profile ID <input required value={m.facultyProfileId} onChange={e => setMembers(members.map((row,n) => n===i ? {...row,facultyProfileId:e.target.value} : row))}/></label><label>Team role <select value={m.role} onChange={e => setMembers(members.map((row,n) => n===i ? {...row,role:e.target.value as 'LEAD' | 'MEMBER'} : row))}><option>MEMBER</option><option>LEAD</option></select></label><button type="button" onClick={() => setMembers(members.filter((_,n) => i!==n))}>Remove member {i+1}</button></fieldset>)}<button type="button" onClick={() => setMembers([...members,{facultyProfileId:'',role:'MEMBER'}])}>Add team member</button><button type="submit">Save team</button></fieldset></form></section>
}
function MilestoneEditor({projectId, milestone, saved}: {projectId: string; milestone?: ProgressView; saved: () => Promise<void>}) {
  const [status,setStatus]=useState<Progress['status']>(milestone?.status ?? 'PENDING'); const [busy,setBusy]=useState(false); const [error,setError]=useState<unknown>(null); const [message,setMessage]=useState(''); const [reset,setReset]=useState(0)
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();const form=new FormData(e.currentTarget);setError(null);setMessage('')
    const input: Progress={title:value(form,'title'),description:value(form,'description'),dueDate:value(form,'dueDate'),status,outcome:value(form,'outcome') || null}
    if (!input.title || !input.description || (status==='COMPLETED' && !input.outcome)) {setError(new Error('Title and description are required; completed milestones require an outcome.'));return}
    setBusy(true)
    try {if(milestone) await api.editMilestone(projectId,milestone.id,input);else await api.addMilestone(projectId,input); await saved();setMessage('Milestone saved.');if(!milestone){setStatus('PENDING');setReset(n => n+1)}notify()}
    catch(e) {setError(e)} finally {setBusy(false)}
  }
  return <form key={reset} onSubmit={submit}><h3>{milestone ? `Update ${milestone.title}` : 'Add milestone'}</h3><ErrorDisplay error={error}/>{message && <p role="status">{message}</p>}<fieldset disabled={busy}><legend>Milestone progress</legend><label>Milestone title <input name="title" required maxLength={255} defaultValue={milestone?.title}/></label><label>Description <textarea name="description" required defaultValue={milestone?.description}/></label><label>Due date <input type="date" name="dueDate" required defaultValue={milestone?.dueDate}/></label><label>Progress <select value={status} onChange={e => setStatus(e.target.value as Progress['status'])}><option>PENDING</option><option>COMPLETED</option></select></label><label>Outcome <textarea name="outcome" required={status==='COMPLETED'} defaultValue={milestone?.outcome ?? ''}/></label><button type="submit">{milestone ? 'Update milestone' : 'Add milestone'}</button></fieldset></form>
}
export function ProjectWorkspace() {
  const {id=''}=useParams();const {user}=useAuth()
  return <WorkspaceContent key={`${id}:${user?.id}`} id={id}/>
}
function WorkspaceContent({id}: {id: string}) {
  const {user}=useAuth();const [project,setProject]=useState<Project | null>(null);const [detail,setDetail]=useState<Detail | null>(null);const [error,setError]=useState<unknown>(null);const [actionError,setActionError]=useState<unknown>(null);const [target,setTarget]=useState<Status | null>(null);const [reload,setReload]=useState(0)
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
  useEffect(() => {let active=true;setProject(null);setDetail(null);setError(null);setActionError(null)
    api.project(id).then(async p => {if(!active)return;setProject(p);try {const d=await api.challenge(p.challengeId);if(active)setDetail(d)}catch(e){if(active)setActionError(e)}}).catch(e => {if(active)setError(e)})
    return () => {active=false}
  },[id,reload])
  async function refresh() {const p=await api.project(id);setProject(p);setDetail(await api.challenge(p.challengeId));setTarget(null);notify()}
  const actions=user?.role==='INDUSTRY' ? [] : detail?.allowedTransitions ?? []
  return <><h1>Project workspace</h1><ErrorDisplay error={error}/>{!project ? error ? <button onClick={() => setReload(n => n+1)}>Retry project</button> : <Loading/> : <><h2>{project.title}</h2><p>Project ID: {project.id}</p><p>University ID: {project.universityId}</p><p>Status: {project.status}</p><p><Link to={`/challenges/${project.challengeId}`}>Challenge details</Link> — <Link to={`/universities/${project.universityId}/departments`}>University departments</Link></p>{user?.role!=='INDUSTRY' && <><h2>Proposal</h2><p>{project.proposalText ?? 'Proposal is not included in the support-facing project view.'}</p></>}<p>Impact: {project.impactSummary ?? 'Not recorded'}</p>{user?.role!=='INDUSTRY' && <><h2>Team</h2>{project.team ? <><p>{project.team.name}</p><ul>{project.team.members.map(m => <li key={m.id}>{m.name} — {m.role} ({m.facultyProfileId})</li>)}</ul></> : <p>No team information.</p>}</>}
    {user?.role==='UNIVERSITY' && <><ProposalEditor key={project.id} challengeId={project.challengeId} project={project} saved={setProject}/><TeamEditor project={project} saved={setProject}/></>}
    <section><h2>Milestones</h2>{!project.milestones.length && <p>No milestones.</p>}{project.milestones.map(m => <article key={m.id}><h3>{m.title}</h3><p>{m.description}</p><p>Due: {m.dueDate} — {m.status}</p><p>Outcome: {m.outcome ?? 'Not recorded'}</p>{m.completedAt && <p>Completed: {new Date(m.completedAt).toLocaleString()}</p>}{user?.role==='UNIVERSITY' && <MilestoneEditor projectId={project.id} milestone={m} saved={refresh}/>}</article>)}{user?.role==='UNIVERSITY' && <MilestoneEditor projectId={project.id} saved={refresh}/>}</section>
    {user?.role==='INDUSTRY' && <PledgeForm projectId={project.id} saved={refresh}/>}
    <section><h2>Lifecycle actions</h2><ErrorDisplay error={actionError}/>{!!actionError && <button onClick={() => setReload(n => n+1)}>Reload lifecycle actions</button>}{detail && <>{!actions.length && <p>No lifecycle actions available.</p>}{actions.map(t => <button key={t} disabled={!!target} onClick={() => setTarget(t)}>Move to {t}</button>)}{target && actions.includes(target) && <ActionForm target={target} detail={detail} done={refresh} cancel={() => setTarget(null)}/>}</>}</section>
  </>}</>
}

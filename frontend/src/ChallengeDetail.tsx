import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { api } from './api'
import { useAuth } from './auth-context'
import { ErrorDisplay, Loading } from './components'
import type { Detail, Status, Category, Support } from './types'
import { categories } from './challenge-options'
export function Timeline({events}: {events: Detail['timeline']}) {
  return <section><h2>Lifecycle timeline</h2>{events.length ? <ol>{[...events].sort((a,b) => Date.parse(a.createdAt)-Date.parse(b.createdAt)).map((e,i) => <li key={i}>{e.fromState ?? 'New report'} → {e.toState} — <time dateTime={e.createdAt}>{new Date(e.createdAt).toLocaleString()}</time></li>)}</ol> : <p>No lifecycle events.</p>}</section>
}
export function ActionForm({target, detail, done, cancel}: {target: Status; detail: Detail; done: () => Promise<void>; cancel: () => void}) {
  const [busy,setBusy] = useState(false); const [error,setError] = useState<unknown>(null)
  const [supportType,setSupportType] = useState<Support['supportType']>('FUNDING')
  const reasonRequired = ['REJECTED','PILOT','DEPLOYED'].includes(target)
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = new FormData(e.currentTarget); const value = (key: string) => String(form.get(key) ?? '').trim()
    setBusy(true); setError(null)
    try {
      if (target === 'ASSIGNED') await api.assign(detail.id,value('universityId'))
      else if (target === 'PROPOSAL_CREATED') await api.createProject(detail.id,{title:value('title'),proposalText:value('proposalText'),teamName:value('teamName')})
      else if (target === 'INDUSTRY_CONNECTED') {
        const project = await api.project(value('projectId'))
        if (project.challengeId !== detail.id) throw new Error('The selected project belongs to another challenge.')
        await api.pledge(project.id,{supportType:value('supportType') as Support['supportType'],amount:supportType === 'FUNDING' ? Number(value('amount')) : undefined,currency:supportType === 'FUNDING' ? value('currency') : undefined,description:value('description')})
      } else await api.transition(detail.id,{toState:target,reason:value('reason') || undefined,category:(value('category') || undefined) as Category | undefined,impactSummary:value('impactSummary') || undefined})
      await done()
    } catch(e) {setError(e)} finally {setBusy(false)}
  }
  return <form onSubmit={submit}><h3>Move to {target}</h3><ErrorDisplay error={error}/><fieldset disabled={busy}><legend>Action details</legend>
    {target === 'ASSIGNED' && <label>University ID <input name="universityId" required/></label>}
    {target === 'PROPOSAL_CREATED' && <><p>The university must have accepted the assignment before creating a proposal.</p><label>Project title <input name="title" required maxLength={255}/></label><label>Proposal <textarea name="proposalText" required/></label><label>Team name <input name="teamName" required maxLength={255}/></label></>}
    {target === 'INDUSTRY_CONNECTED' && <><label>Project ID <input name="projectId" required/></label><label>Support type <select name="supportType" value={supportType} onChange={e => setSupportType(e.target.value as Support['supportType'])}>{['FUNDING','IN_KIND','MENTORSHIP','EQUIPMENT','PILOT_SITE'].map(t => <option key={t}>{t}</option>)}</select></label>{supportType === 'FUNDING' && <><label>Amount (required for funding) <input name="amount" required={supportType === 'FUNDING'} type="number" min="0.01" step="0.01"/></label><label>Currency (required for funding) <input name="currency" required={supportType === 'FUNDING'} pattern="[A-Z]{3}"/></label></>}<label>Support description <textarea name="description" required/></label></>}
    {target === 'CATEGORIZED' && detail.status !== 'ASSIGNED' && <label>Category <select name="category" required defaultValue={detail.category ?? ''}><option value="">Choose a category</option>{categories.map(c => <option key={c}>{c}</option>)}</select></label>}
    {!['ASSIGNED','PROPOSAL_CREATED','INDUSTRY_CONNECTED'].includes(target) && <label>Reason{reasonRequired ? ' (required)' : ' (optional)'} <textarea name="reason" required={reasonRequired}/></label>}
    {target === 'IMPACT_EVALUATED' && <label>Impact summary <textarea name="impactSummary" required/></label>}
    {target === 'PROTOTYPE' && <p>A completed project milestone with prototype evidence must already exist.</p>}
    <button type="submit">Submit action</button><button type="button" onClick={cancel}>Cancel</button></fieldset>{busy && <Loading/>}</form>
}
export default function ChallengeDetail() {
  const {id = ''} = useParams(); const {user,loading:authLoading} = useAuth()
  return <DetailContent key={`${id}:${user?.id ?? 'public'}:${authLoading}`} id={id}/>
}
function DetailContent({id}: {id: string}) {
  const {user,loading:authLoading} = useAuth(); const [detail,setDetail] = useState<Detail | null>(null); const [error,setError] = useState<unknown>(null)
  const [owned,setOwned] = useState(false); const [ownershipError,setOwnershipError] = useState<unknown>(null); const [target,setTarget] = useState<Status | null>(null); const [busy,setBusy] = useState(false)
  const [reload,setReload] = useState(0)
  useEffect(() => {
    if (authLoading) return
    let active = true
    api.challenge(id).then(d => {if(active)setDetail(d)}).catch(e => {if(active)setError(e)})
    if (user?.role === 'CITIZEN') void (async () => {
      try {let page=0; while(active) {const result=await api.ownChallenges({page,size:100}); if(result.content.some(c => c.id === id)) {if(active)setOwned(true); break} if(result.last)break; page++}}
      catch(e) {if(active)setOwnershipError(e)}
    })()
    return () => {active=false}
  }, [id,user,authLoading,reload])
  async function refresh() { const updated=await api.challenge(id); setDetail(updated); setTarget(null) }
  async function confirm() {setBusy(true); setError(null); try {await api.confirmFixed(id); await refresh()} catch(e) {setError(e)} finally {setBusy(false)}}
  return <><ErrorDisplay error={error}/>{!detail ? error ? <button onClick={() => {setError(null);setReload(n => n+1)}}>Retry</button> : <Loading/> : <><h1>{detail.title}</h1><p>{detail.description}</p><dl><dt>Status</dt><dd>{detail.status}</dd><dt>Category</dt><dd>{detail.category ?? 'Uncategorized'}</dd><dt>District</dt><dd>{detail.district}</dd><dt>Coordinates</dt><dd>{detail.latitude}, {detail.longitude}</dd><dt>Affected people</dt><dd>{detail.affectedPeople}</dd><dt>Submitted</dt><dd>{new Date(detail.createdAt).toLocaleString()}</dd><dt>Photo attached</dt><dd>{detail.hasPhoto ? 'Yes' : 'No'}</dd><dt>Audio attached</dt><dd>{detail.hasAudio ? 'Yes' : 'No'}</dd><dt>AI status</dt><dd>{detail.aiStatus}</dd><dt>Citizen confirmation</dt><dd>{detail.fixedConfirmedAt ? new Date(detail.fixedConfirmedAt).toLocaleString() : 'Not confirmed'}</dd></dl><Timeline events={detail.timeline}/>
    <section><h2>Actions</h2>{detail.allowedTransitions.map(t => <button key={t} disabled={!!target || busy} onClick={() => setTarget(t)}>Move to {t}</button>)}{!detail.allowedTransitions.length && <p>No lifecycle actions available.</p>}{target && detail.allowedTransitions.includes(target) && <ActionForm key={target} target={target} detail={detail} done={refresh} cancel={() => setTarget(null)}/>}</section>
    <ErrorDisplay error={ownershipError}/>{owned && user?.role === 'CITIZEN' && ['DEPLOYED','IMPACT_EVALUATED'].includes(detail.status) && !detail.fixedConfirmedAt && <button disabled={busy} onClick={() => void confirm()}>Confirm fixed</button>}{busy && <Loading/>}</>}</>
}

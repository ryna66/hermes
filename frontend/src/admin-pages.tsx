import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from './api'
import { categories } from './challenge-options'
import { ErrorDisplay, Loading } from './components'
import type { Category, Match, Page, Review } from './types'

type Queue = 'moderation' | 'allocation' | 'overdue'
const queues: Queue[] = ['moderation', 'allocation', 'overdue']
type Perform = (action: () => Promise<unknown>, message: string) => Promise<void>
const uuidPattern = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'

function ReviewActions({review, perform}: {review: Review; perform: Perform}) {
  const c = review.challenge
  const [category, setCategory] = useState<Category | ''>(c.category ?? review.suggestedCategory ?? '')
  const [reason, setReason] = useState('')
  const [originalId, setOriginalId] = useState('')
  const [duplicates, setDuplicates] = useState<Awaited<ReturnType<typeof api.duplicates>> | null>(null)
  const [matches, setMatches] = useState<Match[] | null>(null)
  const [universityId, setUniversityId] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(false)
  const unallocated = ['SUBMITTED', 'VALIDATED', 'CATEGORIZED'].includes(c.status)
  async function load(kind: 'duplicates' | 'matches') {
    setLoading(true); setError(null)
    try {
      if (kind === 'duplicates') setDuplicates(await api.duplicates(c.id))
      else setMatches(await api.universityMatches(c.id))
    } catch (err) {setError(err)} finally {setLoading(false)}
  }
  function reject(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!reason.trim()) {setError(new Error('Rejection reason is required.')); return}
    void perform(() => api.transition(c.id, {toState:'REJECTED', reason:reason.trim()}), `${c.title}: rejected.`)
  }
  return <article aria-label={c.title}>
    <h2><Link to={`/challenges/${encodeURIComponent(c.id)}`}>{c.title}</Link></h2>
    <p>{c.description}</p><p>Status: {c.status}; district: {c.district}</p>
    <dl><dt>Current category</dt><dd>{c.category ?? 'Uncategorized'}</dd>
      <dt>AI suggestion</dt><dd>{review.suggestedCategory ?? 'None'}</dd>
      <dt>Confidence</dt><dd>{review.confidence ?? 'Unavailable'}</dd>
      <dt>Moderation reason</dt><dd>{review.moderationReason ?? 'None'}</dd>
      <dt>Duplicate flagged</dt><dd>{review.duplicateCandidate ? 'Yes' : 'No'}</dd></dl>
    {review.assignedUniversityId && <p>Assigned university: {review.assignedUniversityId}; response: {review.assignmentResponse ?? 'None'}; deadline: {review.responseDeadline ?? 'None'}</p>}
    <ErrorDisplay error={error}/>
    <fieldset disabled={loading}><legend>Challenge actions</legend>
      {unallocated && <>
        <form onSubmit={e => {e.preventDefault(); if (category) void perform(() => api.classify(c.id, category), `${c.title}: category confirmed.`)}}>
          <label>Category <select required value={category} onChange={e => setCategory(e.target.value as Category | '')}><option value="">Choose a category</option>{categories.map(cat => <option key={cat}>{cat}</option>)}</select></label>
          <button>Confirm category</button>
        </form>
        <button type="button" onClick={() => void load('duplicates')}>View duplicate candidates</button>
        {duplicates && <section><h3>Duplicate candidates</h3>{duplicates.length ? <ul>{duplicates.map(d => <li key={d.id}><Link to={`/challenges/${encodeURIComponent(d.id)}`}>{d.title}</Link> — similarity: {d.similarity}; distance: {d.distanceKm} km <button type="button" onClick={() => setOriginalId(d.id)}>Use {d.title} as original</button></li>)}</ul> : <p>No duplicate candidates.</p>}</section>}
        <form onSubmit={e => {e.preventDefault(); if (originalId.trim()) void perform(() => api.merge(c.id, originalId.trim()), `${c.title}: merged into ${originalId.trim()}.`)}}>
          <p>This report will be merged into the original report selected below.</p>
          <label>Original challenge ID <input required pattern={uuidPattern} value={originalId} onChange={e => setOriginalId(e.target.value)}/></label><button>Merge duplicate</button>
        </form>
        {c.status === 'SUBMITTED' && <button type="button" onClick={() => void perform(() => api.transition(c.id, {toState:'VALIDATED'}), `${c.title}: validated.`)}>Validate</button>}
        {c.status === 'VALIDATED' && <button type="button" disabled={!category} onClick={() => {if (category) void perform(() => api.transition(c.id, {toState:'CATEGORIZED', category}), `${c.title}: categorized.`)}}>Categorize</button>}
        <form onSubmit={reject}><label>Rejection reason <textarea required value={reason} onChange={e => setReason(e.target.value)}/></label><button>Reject</button></form>
      </>}
      {c.status === 'CATEGORIZED' && <section><h3>University allocation</h3>
        <button type="button" onClick={() => void load('matches')}>View ranked universities</button>
        {matches && <>{matches.length ? <ol>{matches.map(m => <li key={m.id}><p>{m.name} — score: {m.score}</p><ul>{m.reasons.map((r, i) => <li key={i}>{r}</li>)}</ul><button type="button" onClick={() => setUniversityId(m.id)}>Select {m.name}</button></li>)}</ol> : <p>No ranked universities available. Enter a university ID to assign manually.</p>}</>}
        <form onSubmit={e => {e.preventDefault(); if (universityId.trim()) void perform(() => api.assign(c.id, universityId.trim()), `${c.title}: university assigned.`)}}>
          <p>Choose any ranked university or enter another university's organization UUID.</p>
          <label>University ID <input required pattern={uuidPattern} value={universityId} onChange={e => setUniversityId(e.target.value)}/></label><button>Assign university</button>
        </form>
      </section>}
      {c.status === 'ASSIGNED' && review.reassignmentRequired && <button type="button" onClick={() => void perform(() => api.transition(c.id, {toState:'CATEGORIZED', reason:'Recall overdue assignment'}), `${c.title}: overdue assignment recalled.`)}>Recall overdue assignment</button>}
    </fieldset>{loading && <Loading/>}
  </article>
}

export function AdminQueue({initialQueue = 'moderation'}: {initialQueue?: Queue}) {
  const [params, setParams] = useSearchParams()
  const requested = params.get('queue') as Queue
  const queue = queues.includes(requested) ? requested : initialQueue
  const [page, setPage] = useState(0)
  const [reload, setReload] = useState(0)
  const [result, setResult] = useState<Page<Review> | null>(null)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [actionError, setActionError] = useState<unknown>(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => {
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
    let active = true; setResult(null); setLoadError(null)
    api.adminChallenges({queue, page, size:20}).then(p => {
      if (!active) return
      if (!p.content.length && page > 0) setPage(page - 1)
      else setResult(p)
    }).catch(err => {if (active) setLoadError(err)})
    return () => {active = false}
  }, [queue, page, reload])
  const perform: Perform = async (action, success) => {
    setBusy(true); setMessage(''); setActionError(null)
    try {await action(); setMessage(success)} catch (err) {setActionError(err)}
    finally {setReload(n => n + 1); setBusy(false)}
  }
  return <><h1>{initialQueue === 'allocation' ? 'Allocation' : 'Validation queue'}</h1>
    <div role="tablist" aria-label="Challenge queues">{queues.map(q => <button key={q} role="tab" aria-selected={queue === q} aria-controls="queue-results" id={`tab-${q}`} disabled={busy} onClick={() => {setPage(0); setParams({queue:q}); setMessage(''); setActionError(null)}}>{q[0].toUpperCase() + q.slice(1)}</button>)}</div>
    {message && <p role="status">{message}</p>}<ErrorDisplay error={actionError}/><ErrorDisplay error={loadError}/>
    <button disabled={busy} onClick={() => setReload(n => n + 1)}>Refresh queue</button>
    <section role="tabpanel" id="queue-results" aria-labelledby={`tab-${queue}`}>
      {!result && !loadError && <Loading/>}
      {result && <><fieldset disabled={busy}><legend>{queue} queue</legend>{result.content.length ? result.content.map(r => <ReviewActions key={`${queue}:${page}:${reload}:${r.challenge.id}`} review={r} perform={perform}/>) : <p>No challenges in this queue.</p>}</fieldset>
        <nav aria-label="Queue pagination"><p>Page {result.number + 1} of {Math.max(1, result.totalPages)}</p><button disabled={busy || result.number === 0} onClick={() => setPage(result.number - 1)}>Previous</button><button disabled={busy || result.last} onClick={() => setPage(result.number + 1)}>Next</button></nav></>}
    </section></>
}

export function AccountProvisioning() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [message, setMessage] = useState('')
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget; const data = new FormData(form)
    const value = (key: string) => String(data.get(key) ?? '').trim()
    setBusy(true); setError(null); setMessage('')
    try {
      if (!value('name')) throw new Error('Name is required.')
      const account = await api.provision({name:value('name'), phone:value('phone'), role:value('role') as 'UNIVERSITY' | 'INDUSTRY' | 'GOVERNMENT', organizationId:value('organizationId')})
      setMessage(`Created ${account.role} account for ${account.name} (${account.id}).`); form.reset()
    } catch (err) {setError(err)} finally {setBusy(false)}
  }
  return <><h1>Account provisioning</h1><ErrorDisplay error={error}/>{message && <p role="status">{message}</p>}
    <p>Enter an existing organization UUID whose type matches the account role.</p>
    <form onSubmit={submit}><fieldset disabled={busy}><legend>New account</legend>
      <label>Name <input name="name" required maxLength={200}/></label>
      <label>Phone <input name="phone" type="tel" required pattern="\+?[0-9]{10,15}"/></label>
      <label>Role <select name="role">{['UNIVERSITY','INDUSTRY','GOVERNMENT'].map(r => <option key={r}>{r}</option>)}</select></label>
      <label>Organization ID <input name="organizationId" required pattern={uuidPattern}/></label>
      <button>Create account</button>
    </fieldset></form>{busy && <Loading/>}</>
}

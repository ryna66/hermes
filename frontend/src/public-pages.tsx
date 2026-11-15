import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from './api'
import { ErrorDisplay, Loading } from './components'
import type { Challenge, Page } from './types'
import ChallengeMap from './ChallengeMap'
import { categories, statuses } from './challenge-options'
import { explorerFilters } from './explorer-filters'
export function ChallengeList({items}: {items: Challenge[]}) {
  return items.length ? <ul>{items.map(c => <li key={c.id}><Link to={`/challenges/${encodeURIComponent(c.id)}`}>{c.title}</Link><p>{c.district} — {c.category ?? 'Uncategorized'} — {c.status}</p><p>{c.description}</p><p>Coordinates: {c.latitude}, {c.longitude}</p></li>)}</ul> : <p>No challenges found.</p>
}
export function Landing() {
  const [items,setItems] = useState<Challenge[] | null>(null); const [error,setError] = useState<unknown>(null)
  useEffect(() => { let active = true; api.challenges({page:0,size:5}).then(p => {if(active)setItems(p.content)}).catch(e => {if(active)setError(e)}); return () => {active=false} }, [])
  return <><h1>SIH26043 Societal Innovation Platform</h1><p>Report societal problems, connect universities with local needs, and support solutions through industry and government collaboration.</p><ul><li><Link to="/login">Login / Register</Link></li><li><Link to="/submit">Submit a challenge</Link></li><li><Link to="/explore">Explore challenges</Link></li></ul><h2>Recent public challenges</h2><ErrorDisplay error={error}/>{items ? <ChallengeList items={items}/> : !error && <Loading/>}</>
}
export function Explorer() {
  const [params,setParams] = useSearchParams(); const search = params.toString()
  const [result,setResult] = useState<Page<Challenge> | null>(null); const [error,setError] = useState<unknown>(null); const [loading,setLoading] = useState(true)
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
  useEffect(() => { let active = true; setLoading(true); setError(null); setResult(null); api.challenges(explorerFilters(new URLSearchParams(search))).then(p => {if(active)setResult(p)}).catch(e => {if(active)setError(e)}).finally(() => {if(active)setLoading(false)}); return () => {active=false} }, [search])
  function paginate(page: number) { const next = new URLSearchParams(params); next.set('page',String(page)); setParams(next) }
  return <><h1>Public problem explorer</h1><form key={search} onSubmit={e => {e.preventDefault(); const form = new FormData(e.currentTarget); const next = new URLSearchParams(); for (const field of ['district','category','status']) {const value = String(form.get(field) ?? '').trim(); if(value)next.set(field,value)} setParams(next)}}>
    <label>District <input name="district" defaultValue={params.get('district') ?? ''}/></label>
    <label>Category <select name="category" defaultValue={params.get('category') ?? ''}><option value="">All categories</option>{categories.map(c => <option key={c}>{c}</option>)}</select></label>
    <label>Status <select name="status" defaultValue={params.get('status') ?? ''}><option value="">All statuses</option>{statuses.map(s => <option key={s}>{s}</option>)}</select></label><button>Apply filters</button><button type="button" onClick={() => setParams({})}>Clear filters</button></form>
    <ErrorDisplay error={error}/>{loading && <Loading/>}{result && <><p>{result.totalElements} challenges. Page {result.number + 1} of {Math.max(1,result.totalPages)}.</p><ChallengeList items={result.content}/><nav aria-label="Pagination"><button disabled={result.number === 0} onClick={() => paginate(result.number-1)}>Previous</button><button disabled={result.last} onClick={() => paginate(result.number+1)}>Next</button></nav><ChallengeMap items={result.content}/></>}
  </>
}

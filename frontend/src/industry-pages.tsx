import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'
import { ErrorDisplay, Loading } from './components'
import type { Page, Project, SupportView } from './types'

function ProjectList({projects}: {projects: Project[]}) {
  return projects.length ? <ul>{projects.map(p => <li key={p.id}><Link to={`/projects/${p.id}`}>{p.title}</Link> — {p.status}<p>University: <Link to={`/universities/${p.universityId}/departments`}>{p.universityId}</Link></p><p>Impact: {p.impactSummary ?? 'Not recorded'}</p></li>)}</ul> : <p>No projects on this page.</p>
}
function Discovery() {
  const [page,setPage]=useState(0); const [result,setResult]=useState<Page<Project> | null>(null); const [error,setError]=useState<unknown>(null);const [reload,setReload]=useState(0)
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
  useEffect(() => {let active=true;setResult(null);setError(null)
    api.industryProjects({page,size:20}).then(r => {if(active)setResult(r)}).catch(e => {if(active)setError(e)})
    return () => {active=false}
  },[page,reload])
  return <section><h2>Discover projects</h2><button onClick={() => setReload(n => n+1)}>Refresh projects</button><ErrorDisplay error={error}/>{!result ? !error && <Loading/> : <><p>Page {result.number+1}; {result.totalElements} projects available for support</p><h3>Projects ready for support</h3><p>Awaiting their first industry pledge.</p><ProjectList projects={result.content.filter(p => p.status==='PROPOSAL_CREATED')}/><h3>Projects already supported</h3><p>These projects have received industry support and can receive further pledges.</p><ProjectList projects={result.content.filter(p => p.status!=='PROPOSAL_CREATED')}/><button disabled={page===0} onClick={() => setPage(p => p-1)}>Previous projects</button><button disabled={result.last} onClick={() => setPage(p => p+1)}>Next projects</button></>}</section>
}
function OwnPledges() {
  const [page,setPage]=useState(0);const [result,setResult]=useState<Page<SupportView> | null>(null);const [error,setError]=useState<unknown>(null);const [reload,setReload]=useState(0)
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
  useEffect(() => {let active=true;setResult(null);setError(null)
    api.industryPledges({page,size:20}).then(r => {if(active)setResult(r)}).catch(e => {if(active)setError(e)})
    return () => {active=false}
  },[page,reload])
  return <section><h2>Your organization's pledges</h2><button onClick={() => setReload(n => n+1)}>Refresh pledges</button><ErrorDisplay error={error}/>{!result ? !error && <Loading/> : <><p>Page {result.number+1}; {result.totalElements} pledges</p>{result.content.length ? <table><caption>Own pledges</caption><thead><tr><th scope="col">Project</th><th scope="col">Type</th><th scope="col">Amount</th><th scope="col">Currency</th><th scope="col">Description</th></tr></thead><tbody>{result.content.map(p => <tr key={p.id}><td><Link to={`/projects/${p.projectId}`}>{p.projectId}</Link></td><td>{p.supportType}</td><td>{p.amount ?? '—'}</td><td>{p.currency ?? '—'}</td><td>{p.description}</td></tr>)}</tbody></table> : <p>No pledges.</p>}<button disabled={page===0} onClick={() => setPage(p => p-1)}>Previous pledges</button><button disabled={result.last} onClick={() => setPage(p => p+1)}>Next pledges</button></>}</section>
}
export function IndustryDashboard() {return <><h1>Industry dashboard</h1><Discovery/><OwnPledges/></>}

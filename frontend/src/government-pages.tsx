import { useEffect, useState } from 'react'
import type { ReactNode, FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Bar, BarChart, XAxis, YAxis, Tooltip } from 'recharts'
import { api } from './api'
import { ErrorDisplay, Loading } from './components'
import type { Dashboard, Project } from './types'

export function CountChart({title, counts}: {title: string; counts: Record<string,number>}) {
  const data=Object.entries(counts).map(([name,count]) => ({name,count}))
  return <figure aria-label={title}><figcaption>{title}</figcaption>{!data.length && <p>No chart data.</p>}<BarChart width={640} height={300} data={data} accessibilityLayer><XAxis dataKey="name"/><YAxis allowDecimals={false}/><Tooltip/><Bar dataKey="count" isAnimationActive={false}/></BarChart></figure>
}
function CountTable({caption, label, rows, renderLabel, unit='Count'}: {caption: string; label: string; rows: [string,number][]; renderLabel?: (key: string) => ReactNode; unit?: string}) {
  return <table><caption>{caption}</caption><thead><tr><th scope="col">{label}</th><th scope="col">{unit}</th></tr></thead><tbody>{rows.length ? rows.map(([key,count]) => <tr key={key}><th scope="row">{renderLabel ? renderLabel(key) : key}</th><td>{count}</td></tr>) : <tr><td colSpan={2}>No data.</td></tr>}</tbody></table>
}
function ProjectLookup() {
  const [project,setProject]=useState<Project | null>(null);const [error,setError]=useState<unknown>(null);const [busy,setBusy]=useState(false)
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();const id=String(new FormData(e.currentTarget).get('projectId') ?? '').trim();if(!id)return
    setProject(null);setError(null);setBusy(true)
    try {setProject(await api.project(id))}catch(e){setError(e)}finally{setBusy(false)}
  }
  return <section><h2>Project drill-down</h2><p>Enter a known project ID to review outcomes and available lifecycle actions. The analytics summary does not include project IDs.</p><form onSubmit={submit}><fieldset disabled={busy}><legend>Find project</legend><label>Project ID <input name="projectId" required onChange={() => {setProject(null);setError(null)}}/></label><button>Find project</button></fieldset></form><ErrorDisplay error={error}/>{project && <p><Link to={`/projects/${project.id}`}>Open {project.title}</Link> — {project.status}</p>}</section>
}
export function GovernmentAnalytics() {
  const [data,setData]=useState<Dashboard | null>(null);const [error,setError]=useState<unknown>(null);const [reload,setReload]=useState(0)
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
  useEffect(() => {let active=true;setData(null);setError(null);api.analytics().then(d => {if(active)setData(d)}).catch(e => {if(active)setError(e)});return () => {active=false}},[reload])
  return <><h1>Government analytics</h1><button onClick={() => setReload(n => n+1)}>Refresh analytics</button><ErrorDisplay error={error}/>{!data ? !error && <Loading/> : <>
    <section><h2>Reports and outcomes</h2><CountTable caption="Report totals and outcomes" label="Measure" rows={[
      ['Total reports',Object.values(data.byStatus).reduce((sum,count) => sum+count,0)],['Unique incidents',data.uniqueIncidents],['Open',data.open],['Resolved',data.resolved],['Rejected',data.rejected],
    ]}/><p>Unique incidents exclude merged duplicate reports. Status, category and district counts include all reports. Resolved reports are DEPLOYED or IMPACT_EVALUATED.</p></section>
    <section><h2>Status</h2><CountTable caption="Reports by status" label="Status" rows={Object.entries(data.byStatus)} renderLabel={key => <Link to={`/explore?status=${encodeURIComponent(key)}`}>{key}</Link>}/><CountChart title="Reports by status chart" counts={data.byStatus}/></section>
    <section><h2>Category</h2><CountTable caption="Reports by category" label="Category" rows={Object.entries(data.byCategory)} renderLabel={key => key==='UNCATEGORIZED' ? key : <Link to={`/explore?category=${encodeURIComponent(key)}`}>{key}</Link>}/><CountChart title="Reports by category chart" counts={data.byCategory}/></section>
    <section><h2>Districts</h2><CountTable caption="Districts by report count" label="District" rows={Object.entries(data.byDistrict).sort((a,b) => b[1]-a[1] || a[0].localeCompare(b[0]))} renderLabel={key => <Link to={`/explore?district=${encodeURIComponent(key)}`}>{key}</Link>}/><p>Unique incidents across all districts: {data.uniqueIncidents}. District-level unique incident counts are not available.</p></section>
    <section><h2>University participation</h2><CountTable caption="Reports assigned by university" label="University ID" rows={Object.entries(data.universityParticipation)} renderLabel={key => <Link to={`/universities/${encodeURIComponent(key)}/departments`}>{key}</Link>}/></section>
    <section><h2>Pledges</h2><CountTable caption="Pledges by industry organization" label="Industry organization ID" rows={Object.entries(data.industryPledges)}/><CountTable caption="Pledges by type" label="Support type" rows={Object.entries(data.pledgesByType)}/><CountTable caption="Funding by currency" label="Currency" unit="Amount" rows={Object.entries(data.fundingByCurrency)}/></section>
    <section><h2>Lifecycle outcomes</h2><table><caption>Completed lifecycle stage visits</caption><thead><tr><th scope="col">Stage</th><th scope="col">Average seconds</th><th scope="col">Samples</th></tr></thead><tbody>{Object.keys(data.stageSamples).length ? Object.entries(data.stageSamples).map(([stage,count]) => <tr key={stage}><th scope="row">{stage}</th><td>{data.averageStageSeconds[stage] ?? '—'}</td><td>{count}</td></tr>) : <tr><td colSpan={3}>No completed stage visits.</td></tr>}</tbody></table></section>
  </>}<ProjectLookup/></>
}

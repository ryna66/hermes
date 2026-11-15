import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'
import { ErrorDisplay, Loading } from './components'
import type { Notification, OwnChallenge, Page } from './types'
function Pagination({result, change}: {result: Page<unknown>; change: (page: number) => void}) {
  return <nav aria-label="Pagination"><p>Page {result.number + 1} of {Math.max(1, result.totalPages)}</p><button disabled={result.number === 0} onClick={() => change(result.number - 1)}>Previous</button><button disabled={result.last} onClick={() => change(result.number + 1)}>Next</button></nav>
}
export function MyChallenges() {
  const [page, setPage] = useState(0); const [result, setResult] = useState<Page<OwnChallenge> | null>(null)
  const [error, setError] = useState<unknown>(null)
  useEffect(() => {
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
    let active = true; setResult(null); setError(null)
    api.ownChallenges({page, size:20}).then(p => {if (active) setResult(p)}).catch(e => {if (active) setError(e)})
    return () => {active = false}
  }, [page])
  return <><h1>My challenges</h1><Link to="/submit">Submit a challenge</Link><ErrorDisplay error={error}/>{!result && !error && <Loading/>}{result && <>{result.content.length ? <ul>{result.content.map(c => <li key={c.id}><Link to={`/challenges/${encodeURIComponent(c.id)}`}>{c.title}</Link><p>Status: {c.status}</p>{c.status === 'REJECTED' && <p>Rejection reason: {c.rejectionReason || 'No reason provided.'}</p>}</li>)}</ul> : <p>No challenges submitted.</p>}<Pagination result={result} change={setPage}/></>}</>
}
export function Notifications() {
  const [page, setPage] = useState(0); const [result, setResult] = useState<Page<Notification> | null>(null)
  const [error, setError] = useState<unknown>(null); const [reading, setReading] = useState<string | null>(null)
  const generation = useRef(0)
  useEffect(() => {
  // eslint-disable-next-line react-hooks/set-state-in-effect -- Reset stale results/errors while synchronizing a new external API request.
    const current = ++generation.current; setResult(null); setError(null)
    api.notifications({page, size:20}).then(p => {if (generation.current === current) setResult(p)}).catch(e => {if (generation.current === current) setError(e)})
    return () => {generation.current = current + 1}
  }, [page])
  async function read(id: string) {
    setReading(id); setError(null); const current = generation.current
    try {
      const updated = await api.readNotification(id)
      window.dispatchEvent(new Event('notifications-changed'))
      if (generation.current === current) setResult(p => p && {...p, content:p.content.map(n => n.id === id ? updated : n)})
    } catch (err) {if (generation.current === current) setError(err)} finally {setReading(null)}
  }
  return <><h1>Notifications</h1><ErrorDisplay error={error}/>{!result && !error && <Loading/>}{result && <>{result.content.length ? <ul>{result.content.map(n => <li key={n.id}><p>{n.message}</p><time dateTime={n.createdAt}>{n.createdAt}</time>{n.challengeId && <p><Link to={`/challenges/${encodeURIComponent(n.challengeId)}`}>View challenge</Link></p>}{n.readAt ? <p>Read</p> : <><p>Unread</p><button disabled={reading !== null} onClick={() => void read(n.id)}>{reading === n.id ? 'Marking as read…' : 'Mark as read'}</button></>}</li>)}</ul> : <p>No notifications.</p>}<Pagination result={result} change={setPage}/></>}</>
}

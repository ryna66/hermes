import { ApiError } from './api'
export function ErrorDisplay({ error }: { error: unknown }) {
  if (!error) return null
  return <div role="alert"><p>{error instanceof Error ? error.message : 'An unexpected error occurred.'}</p>{error instanceof ApiError && <><p>{error.code} ({error.status})</p><ul>{error.fieldErrors.map((f,i) => <li key={i}>{f.field}: {f.message}</li>)}</ul></>}</div>
}
export function Loading() { return <p role="status">Loading…</p> }

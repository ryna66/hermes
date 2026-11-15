import type * as T from './types'
import { clearSession, tokenForRequest } from './session'
export const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '')
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: { field: string; message: string }[]
  constructor(status: number, body: unknown) {
    const data = body && typeof body === 'object' ? body as Record<string, unknown> : {}
    super(typeof data.message === 'string' ? data.message : `Request failed (${status})`)
    this.name = 'ApiError'; this.status = status
    this.code = typeof data.code === 'string' ? data.code : 'HTTP_ERROR'
    this.fieldErrors = Array.isArray(data.fieldErrors) ? data.fieldErrors.filter((f): f is {field: string; message: string} => !!f && typeof f.field === 'string' && typeof f.message === 'string') : []
  }
}
export async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers = new Headers({ Accept: 'application/json' })
  const token = tokenForRequest()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const multipart = body instanceof FormData
  if (body !== undefined && !multipart) headers.set('Content-Type', 'application/json')
  const response = await fetch(`${API_URL}/api${path}`, { method, headers, body: body === undefined ? undefined : multipart ? body : JSON.stringify(body) })
  if (response.status === 401) clearSession()
  const text = await response.text()
  let data: unknown
  try { data = text ? JSON.parse(text) : undefined } catch { data = undefined }
  if (!response.ok) throw new ApiError(response.status, data)
  return data as T
}
type Paging = { page?: number; size?: number }
function query(params: object = {}) { const q = new URLSearchParams(); Object.entries(params).forEach(([k,v]) => { if (v !== undefined && v !== null) q.set(k, String(v)) }); return q.size ? `?${q}` : '' }
const id = encodeURIComponent
export const api = {
  register: (name: string, phone: string) => request<{expiresAt: string}>('/auth/register', 'POST', { name, phone }),
  requestOtp: (phone: string) => request<{expiresAt: string}>('/auth/otp/request', 'POST', { phone }),
  verifyOtp: (phone: string, otp: string) => request<T.Token>('/auth/otp/verify', 'POST', { phone, otp }),
  me: () => request<T.Account>('/users/me'),
  challenges: (params: Paging & { district?: string; category?: T.Category; status?: T.Status } = {}) => request<T.Page<T.Challenge>>('/challenges' + query(params)),
  challenge: (uuid: string) => request<T.Detail>(`/challenges/${id(uuid)}`),
  submitChallenge: (input: T.ChallengeInput, photo?: File, audio?: File) => {
    if (!photo && !audio) return request<T.Challenge>('/challenges', 'POST', input)
    const form = new FormData(); form.append('challenge', new Blob([JSON.stringify(input)], {type: 'application/json'})); if (photo) form.append('photo', photo); if (audio) form.append('audio', audio)
    return request<T.Challenge>('/challenges', 'POST', form)
  },
  transcribe: (input: {audioBase64: string; mimeType: string; language: string}) => request<{text: string | null; status: string; message: string}>('/challenges/transcription', 'POST', input),
  ownChallenges: (params: Paging = {}) => request<T.Page<T.OwnChallenge>>('/users/me/challenges' + query(params)),
  transition: (uuid: string, input: {toState: T.Status; reason?: string; category?: T.Category; impactSummary?: string}) => request<T.Challenge>(`/challenges/${id(uuid)}/transitions`, 'POST', input),
  assign: (uuid: string, universityId: string) => request<T.Challenge>(`/admin/challenges/${id(uuid)}/assignment`, 'POST', {universityId}),
  respond: (uuid: string, response: T.AssignmentResponse, reason?: string) => request<T.Challenge>(`/challenges/${id(uuid)}/assignment-response`, 'POST', {response, reason}),
  confirmFixed: (uuid: string) => request<T.Challenge>(`/challenges/${id(uuid)}/confirm-fixed`, 'POST'),
  adminChallenges: (params: Paging & {queue?: 'moderation' | 'allocation' | 'overdue'} = {}) => request<T.Page<T.Review>>('/admin/challenges' + query(params)),
  duplicates: (uuid: string) => request<{id: string; title: string; similarity: number; distanceKm: number}[]>(`/admin/challenges/${id(uuid)}/duplicates`),
  universityMatches: (uuid: string) => request<T.Match[]>(`/admin/challenges/${id(uuid)}/university-matches`),
  merge: (uuid: string, originalId: string) => request<T.Review>(`/admin/challenges/${id(uuid)}/merge`, 'POST', {originalId}),
  classify: (uuid: string, category: T.Category) => request<T.Review>(`/admin/challenges/${id(uuid)}/classification`, 'PATCH', {category}),
  departments: (uuid: string) => request<Record<string,T.Faculty[]>>(`/organizations/${id(uuid)}/departments`),
  replaceFaculty: (department: string, input: T.FacultyInput[]) => request<T.Faculty[]>(`/organizations/me/departments/${id(department)}/faculty`, 'PUT', input),
  inbox: (params: Paging & {assignmentResponse?: T.AssignmentResponse} = {}) => request<T.Page<T.Inbox>>('/university/inbox' + query(params)),
  provision: (input: {name: string; phone: string; role: T.Role; organizationId?: string}) => request<T.Account>('/users', 'POST', input),
  draft: (uuid: string) => request<{proposal: string; draft: boolean; aiStatus: string}>(`/challenges/${id(uuid)}/proposal-draft`, 'POST'),
  createProject: (uuid: string, input: T.Proposal) => request<T.Project>(`/challenges/${id(uuid)}/project`, 'POST', input),
  project: (uuid: string) => request<T.Project>(`/projects/${id(uuid)}`),
  editProject: (uuid: string, input: {proposalText?: string; impactSummary?: string}) => request<T.Project>(`/projects/${id(uuid)}`, 'PATCH', input),
  team: (uuid: string, input: T.TeamInput) => request<T.Project>(`/projects/${id(uuid)}/team`, 'PUT', input),
  addMilestone: (uuid: string, input: T.Progress) => request<T.ProgressView>(`/projects/${id(uuid)}/milestones`, 'POST', input),
  editMilestone: (uuid: string, milestoneId: string, input: T.Progress) => request<T.ProgressView>(`/projects/${id(uuid)}/milestones/${id(milestoneId)}`, 'PATCH', input),
  pledge: (uuid: string, input: T.Support) => request<T.SupportView>(`/projects/${id(uuid)}/pledges`, 'POST', input),
  industryProjects: (params: Paging = {}) => request<T.Page<T.Project>>('/industry/projects' + query(params)),
  matchedProblems: (params: Paging = {}) => request<T.Page<T.Project>>('/industry/matched-problems' + query(params)),
  industryPledges: (params: Paging = {}) => request<T.Page<T.SupportView>>('/industry/pledges' + query(params)),
  analytics: () => request<T.Dashboard>('/government/analytics'),
  notifications: (params: Paging = {}) => request<T.Page<T.Notification>>('/notifications' + query(params)),
  readNotification: (uuid: string) => request<T.Notification>(`/notifications/${id(uuid)}/read`, 'PATCH'),
}

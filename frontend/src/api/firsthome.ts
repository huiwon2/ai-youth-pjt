import type { Comparison, ComparisonAnswer, PriorityPreset, Home, HomePreview, HomePut, LocationSearch, PolicyAnswer, PolicyEvaluation, ProfilePatch, Session } from '../types/contracts'
import { demoApi } from '../mocks/firsthome'
import { ApiError } from './errors'

export const demo = import.meta.env.VITE_FIRSTHOME_API_MODE !== 'live'

async function request<T>(path: string, method = 'GET', body?: unknown, revision?: number): Promise<T> {
  const response = await fetch('/api/v1/sessions' + path, {
    method,
    credentials: 'same-origin',
    cache: 'no-store',
    headers: {
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(revision === undefined ? {} : { 'If-Match': '"' + revision + '"' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw new ApiError(response.status, payload?.error?.message ?? '요청에 실패했어요. 다시 시도해 주세요.', payload?.error?.code)
  }
  return response.json()
}

const path = (id: string) => '/' + encodeURIComponent(id)
const liveApi = {
  create: () => request<Session>('', 'POST', {}),
  restore: (id: string) => request<Session>(path(id)),
  search: (id: string, query: string, kind: 'DESTINATION' | 'HOME' = 'DESTINATION') =>
    request<LocationSearch>(path(id) + '/location-search', 'POST', { kind, query }),
  save: (id: string, revision: number, patch: ProfilePatch) =>
    request<Session>(path(id) + '/profile', 'PATCH', patch, revision),
  evaluate: (id: string, revision: number) =>
    request<PolicyEvaluation>(path(id) + '/policy-evaluation?inputRevision=' + revision),
  scenario: (id: string, revision: number) =>
    request<PolicyEvaluation>(path(id) + '/policy-evaluations', 'POST', { inputRevision: revision, overrides: { ageYears: 35 } }),
  previewHome: (id: string, revision: number, locationId: string) =>
    request<HomePreview>(path(id) + '/home-previews', 'POST', { inputRevision: revision, locationId }),
  putHome: (id: string, revision: number, slot: Home['slot'], body: HomePut) =>
    request<Session>(path(id) + '/homes/' + slot, 'PUT', body, revision),
  deleteHome: (id: string, revision: number, slot: Home['slot']) =>
    request<Session>(path(id) + '/homes/' + slot, 'DELETE', undefined, revision),
  compare: (id: string, revision: number) =>
    request<Comparison>(path(id) + '/comparisons', 'POST', { inputRevision: revision }),
  comparison: (id: string, comparisonId: string) =>
    request<Comparison>(path(id) + '/comparisons' + path(comparisonId)),
  savePriority: (id: string, revision: number, priorityPreset: PriorityPreset) =>
    request<Session>(path(id) + '/preferences', 'PATCH', { priorityPreset }, revision),
  askComparison: (id: string, comparisonId: string, revision: number, question: string) =>
    request<ComparisonAnswer>(path(id) + '/comparisons' + path(comparisonId) + '/questions', 'POST', { inputRevision: revision, question }),
  ask: (id: string, revision: number, question: string) =>
    request<PolicyAnswer>(path(id) + '/policy-questions', 'POST', { inputRevision: revision, question }),
}

export const api: typeof liveApi = demo ? demoApi : liveApi
export const sessionKey = demo ? 'firsthome:demo-session' : 'firsthome:live-session'

// Thin fetch wrappers over the backend REST API (ticket #73). No token is ever
// stored here — it's submitted once via connect() and held server-side in the
// session from then on; every other call just relies on the session cookie.

export interface Repository {
  fullName: string
}

export interface PullRequestSummary {
  number: number
  title: string
}

export interface ImportedPullRequest {
  number: number
  title: string
  author: string
  baseRevision: string
  headRevision: string
}

export type ChangeCategory = 'BEHAVIORAL' | 'STRUCTURAL' | 'MECHANICAL' | 'UNKNOWN'

export type ReviewState = 'UNSEEN' | 'UNDERSTANDING' | 'REVIEWED' | 'CONCERN' | 'SKIPPED'

export interface ChangeMapEntry {
  id: number
  description: string
  category: ChangeCategory
  reviewState: ReviewState
  occurrenceCount: number
  exceptionCount: number
}

export interface ChangeMap {
  prTitle: string
  categoryCounts: Record<ChangeCategory, number>
  changes: ChangeMapEntry[]
}

export class NotConnectedError extends Error {}

export class NoPullRequestSelectedError extends Error {}

async function asJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`)
  }
  return response.json() as Promise<T>
}

export async function connect(token: string): Promise<{ authenticatedUsername: string }> {
  const response = await fetch('/api/connect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token }),
  })
  if (!response.ok) {
    const body = (await response.json()) as { error?: string }
    throw new Error(body.error ?? `Could not connect (${response.status})`)
  }
  return response.json()
}

export async function listRepositories(): Promise<Repository[]> {
  return asJson(await fetch('/api/repositories'))
}

export async function listOpenPullRequests(repositoryFullName: string): Promise<PullRequestSummary[]> {
  return asJson(await fetch(`/api/repositories/${repositoryFullName}/pulls`))
}

export async function selectPullRequest(
  repositoryFullName: string,
  number: number,
): Promise<ImportedPullRequest> {
  return asJson(
    await fetch(`/api/repositories/${repositoryFullName}/pulls/${number}/select`, { method: 'POST' }),
  )
}

export async function getChangeMap(): Promise<ChangeMap> {
  const response = await fetch('/api/review/change-map')
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  return asJson(response)
}

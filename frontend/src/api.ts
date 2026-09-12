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
  changeKey: string
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

export interface ChangeDetail {
  changeKey: string
  category: ChangeCategory
  description: string
  symbols: string[]
  files: string[]
  diff: string
}

export type AnnotationScope =
  | { type: 'LINE'; filePath: string; line: number }
  | { type: 'SYMBOL'; symbolDescription: string }
  | { type: 'CHANGE'; changeKey: string }
  | { type: 'REVIEW' }

export interface Annotations {
  comments: string[]
  privateNotes: string[]
}

export type GitHubAction = 'APPROVE' | 'REQUEST_CHANGES'

export interface PreSubmissionSummary {
  reviewedChangeTitles: string[]
  mechanicalChangeTitles: string[]
  concernChangeTitles: string[]
  commentCount: number
  gitHubAction: GitHubAction
}

export interface Submission {
  fullySynced: boolean
  syncedCommentCount: number
  failedCommentCount: number
}

export class NotConnectedError extends Error {}

export class NoPullRequestSelectedError extends Error {}

export class ChangeNotFoundError extends Error {}

export class BlankAnnotationError extends Error {}

export class ReviewAlreadySubmittedError extends Error {}

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

export async function getChangeDetail(changeKey: string): Promise<ChangeDetail> {
  const response = await fetch(`/api/review/changes/${encodeURIComponent(changeKey)}`)
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  if (response.status === 404) {
    throw new ChangeNotFoundError()
  }
  return asJson(response)
}

async function postAnnotation(path: string, scope: AnnotationScope, text: string): Promise<Annotations> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ scope: toScopeRequest(scope), text }),
  })
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  if (response.status === 400) {
    throw new BlankAnnotationError()
  }
  return asJson(response)
}

function toScopeRequest(scope: AnnotationScope) {
  switch (scope.type) {
    case 'LINE':
      return { type: 'LINE', filePath: scope.filePath, line: scope.line }
    case 'SYMBOL':
      return { type: 'SYMBOL', symbolDescription: scope.symbolDescription }
    case 'CHANGE':
      return { type: 'CHANGE', changeKey: scope.changeKey }
    case 'REVIEW':
      return { type: 'REVIEW' }
  }
}

export async function addComment(scope: AnnotationScope, text: string): Promise<Annotations> {
  return postAnnotation('/api/review/comments', scope, text)
}

export async function addPrivateNote(scope: AnnotationScope, text: string): Promise<Annotations> {
  return postAnnotation('/api/review/private-notes', scope, text)
}

export async function setReviewState(changeKey: string, state: ReviewState): Promise<void> {
  const response = await fetch(`/api/review/changes/${encodeURIComponent(changeKey)}/review-state`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ state }),
  })
  if (response.status === 404) {
    throw new ChangeNotFoundError()
  }
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`)
  }
}

export async function getPreSubmissionSummary(): Promise<PreSubmissionSummary> {
  const response = await fetch('/api/review/pre-submission-summary')
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  return asJson(response)
}

export async function submitReview(): Promise<Submission> {
  const response = await fetch('/api/review/submit', { method: 'POST' })
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  if (response.status === 422) {
    throw new ReviewAlreadySubmittedError()
  }
  return asJson(response)
}

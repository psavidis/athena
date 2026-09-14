// Thin fetch wrappers over the backend REST API. GitHub access is via a GitHub
// App installation (see GitHubConnectController): the frontend never sees a
// token — it asks the backend for a URL to redirect the browser to, and GitHub
// hands the installation result back to backend callbacks. Every other call
// just relies on the session cookie.

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

export type TransformationKind =
  | 'RENAME_SYMBOL'
  | 'MOVE_SYMBOL'
  | 'ADD_SYMBOL'
  | 'REMOVE_SYMBOL'
  | 'CHANGE_METHOD_SIGNATURE'
  | 'EXTRACT_METHOD'
  | 'MECHANICAL_REPLACEMENT'
  | 'FORMATTING_ONLY'
  | 'RENAME_CLASS'
  | 'MOVE_CLASS'
  | 'ADD_CLASS'
  | 'REMOVE_CLASS'
  | 'RENAME_FIELD'
  | 'MOVE_FIELD'
  | 'ADD_FIELD'
  | 'REMOVE_FIELD'

export type ReviewState = 'UNSEEN' | 'UNDERSTANDING' | 'REVIEWED' | 'CONCERN' | 'SKIPPED'

export interface ChangeMapEntry {
  id: number
  changeKey: string
  description: string
  category: ChangeCategory
  kind: TransformationKind
  reviewState: ReviewState
  occurrenceCount: number
  exceptionCount: number
}

export interface ClassGroup {
  enclosingType: string
  entries: ChangeMapEntry[]
}

export interface ChangeMap {
  prTitle: string
  categoryCounts: Record<ChangeCategory, number>
  changes: ChangeMapEntry[]
  classGroups: ClassGroup[]
}

export type SemanticDimension = 'STRUCTURAL' | 'PATTERN' | 'FRAMEWORK' | 'RESPONSIBILITY' | 'FEATURE' | 'ARCHITECTURE' | 'INTENT'

export interface SemanticDimensionEntry {
  dimension: SemanticDimension
  conceptName: string
  conceptDescription: string
  inferred: boolean
  confidencePercent: number
  evidence: string[]
  supportingConceptNames: string[]
  /**
   * For a Framework classification representing a mechanism transition (ticket #97,
   * e.g. Spring field-to-constructor injection): how many of `evidence`'s leading
   * entries are the "before" mechanism's diff text — the rest are "after". Absent/zero
   * for every classification that isn't a transition.
   */
  beforeEvidenceCount?: number
}

export interface SemanticProfile {
  dimensions: SemanticDimensionEntry[]
}

export interface ChangeDetail {
  changeKey: string
  category: ChangeCategory
  kind: TransformationKind
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

export interface AiContextBoundary {
  includedChangeTitles: string[]
  excludedUnreviewedChangeTitles: string[]
  excludedGeneratedChangeTitles: string[]
  privateNotesExcluded: boolean
}

export type AiFindingDisposition = 'PENDING' | 'ACCEPTED' | 'DISMISSED'

export interface AiFinding {
  id: string
  description: string
  disposition: AiFindingDisposition
  jumpTargetChangeKey: string | null
}

export class NotConnectedError extends Error {}

export class NoPullRequestSelectedError extends Error {}

export class ChangeNotFoundError extends Error {}

export class BlankAnnotationError extends Error {}

export class ReviewAlreadySubmittedError extends Error {}

export class FindingNotFoundError extends Error {}

export class NoAnalysisTriggeredError extends Error {}

export class AiAnalysisNotConfiguredError extends Error {}

async function asJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`)
  }
  return response.json() as Promise<T>
}

export interface GitHubStatus {
  connected: boolean
  accountLogin: string | null
}

export async function getGitHubStatus(): Promise<GitHubStatus> {
  return asJson(await fetch('/api/github/status'))
}

export async function getGitHubConnectUrl(): Promise<{ url: string }> {
  return asJson(await fetch('/api/github/connect-url'))
}

export interface AiStatus {
  configured: boolean
}

export async function getAiStatus(): Promise<AiStatus> {
  return asJson(await fetch('/api/ai/status'))
}

export async function connectAi(apiKey: string): Promise<AiStatus> {
  const response = await fetch('/api/ai/connect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiKey }),
  })
  if (!response.ok) {
    throw new Error(`Could not save the key (${response.status})`)
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

export interface ModuleNarrative {
  moduleName: string
  changeKeys: string[]
  narrative: string | null
}

export async function getModules(): Promise<ModuleNarrative[]> {
  const response = await fetch('/api/review/modules')
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  return asJson(response)
}

export async function getModuleNarrative(moduleName: string): Promise<ModuleNarrative> {
  const response = await fetch(`/api/review/modules/${encodeURIComponent(moduleName)}/narrative`)
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  if (response.status === 503) {
    throw new Error('AI analysis is not configured')
  }
  return asJson(response)
}

export type ModuleStatus = 'NEW' | 'TOUCHED' | 'IDLE'

export type TechStack = 'SPRING_BOOT_JAVA' | 'JAVA' | 'REACT_TYPESCRIPT' | 'TYPESCRIPT' | 'UNKNOWN'

export interface ModuleTerritory {
  moduleName: string
  status: ModuleStatus
  fileCount: number
  statusSummary: string
  techStack: TechStack
  techStackLabel: string
  changeKeys: string[]
}

export interface ModuleDependency {
  from: string
  to: string
}

export interface ModuleTopology {
  territories: ModuleTerritory[]
  dependencies: ModuleDependency[]
}

export async function getModuleTopology(): Promise<ModuleTopology> {
  const response = await fetch('/api/review/topology')
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  return asJson(response)
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

export async function getSemanticProfile(changeKey: string): Promise<SemanticProfile> {
  const response = await fetch(`/api/review/change-map/${encodeURIComponent(changeKey)}/semantic-profile`)
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

/** The Semantic Change Explorer aggregated across every Change in one module, so the
 * Explorer can be the primary view for a module instead of a per-Change drill-down. */
export async function getModuleSemanticProfile(moduleName: string): Promise<SemanticProfile> {
  const response = await fetch(`/api/review/modules/${encodeURIComponent(moduleName)}/semantic-profile`)
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

/** The Semantic Change Explorer aggregated across every Change in the whole PR — the
 * Explorer's landing scope the moment a PR is opened (ticket #91's approved mockup has
 * no separate category/change-list screen before it). */
export async function getPullRequestSemanticProfile(): Promise<SemanticProfile> {
  const response = await fetch('/api/review/semantic-profile')
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
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

export async function getAiContextBoundary(): Promise<AiContextBoundary> {
  const response = await fetch('/api/review/ai-context-boundary')
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  return asJson(response)
}

export async function triggerAiAnalysis(): Promise<AiFinding[]> {
  const response = await fetch('/api/review/ai-analysis', { method: 'POST' })
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  if (response.status === 503) {
    throw new AiAnalysisNotConfiguredError()
  }
  return asJson(response)
}

async function evaluateFinding(findingId: string, action: 'accept' | 'dismiss'): Promise<AiFinding[]> {
  const response = await fetch(`/api/review/ai-findings/${encodeURIComponent(findingId)}/${action}`, {
    method: 'POST',
  })
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 404) {
    throw new FindingNotFoundError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  if (response.status === 422) {
    throw new NoAnalysisTriggeredError()
  }
  return asJson(response)
}

export async function acceptFinding(findingId: string): Promise<AiFinding[]> {
  return evaluateFinding(findingId, 'accept')
}

export async function dismissFinding(findingId: string): Promise<AiFinding[]> {
  return evaluateFinding(findingId, 'dismiss')
}

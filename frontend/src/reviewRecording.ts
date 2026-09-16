// Thin fetch wrappers over the Review Recording backend API (ticket #203)
// — mirrors liveSession.ts's own conventions (plain fetch, no client
// library), kept in its own module since this is a distinct feature area.

export interface ReviewRecordingSnapshot {
  recordingId: string
  repositoryFullName: string
  pullRequestNumber: number
  active: boolean
  elapsedSeconds: number
  participantCount: number
  participantDisplayNames: string[]
}

export interface ReviewRecordingStartResult {
  recordingId: string
  snapshot: ReviewRecordingSnapshot
}

export class NoReviewSelectedForRecordingError extends Error {}

export class DisclosureNotAcknowledgedError extends Error {}

export class ReviewRecordingNotFoundError extends Error {}

export class ReviewRecordingNotActiveError extends Error {}

async function asJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`Review recording request failed: ${response.status}`)
  }
  return response.json() as Promise<T>
}

function postJson(path: string, body?: unknown): Promise<Response> {
  return fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export async function fetchCaptureDisclosure(): Promise<string> {
  const response = await fetch('/api/review-recordings/capture-disclosure')
  return asJson(response)
}

export async function startReviewRecording(
  displayName: string,
  disclosureAcknowledged: boolean,
): Promise<ReviewRecordingStartResult> {
  const response = await postJson('/api/review-recordings', { displayName, disclosureAcknowledged })
  if (response.status === 409) {
    throw new NoReviewSelectedForRecordingError()
  }
  if (response.status === 400) {
    throw new DisclosureNotAcknowledgedError()
  }
  return asJson(response)
}

export async function stopReviewRecording(recordingId: string): Promise<ReviewRecordingSnapshot> {
  const response = await postJson(`/api/review-recordings/${encodeURIComponent(recordingId)}/stop`)
  if (response.status === 404) {
    throw new ReviewRecordingNotFoundError()
  }
  if (response.status === 409) {
    throw new ReviewRecordingNotActiveError()
  }
  return asJson(response)
}

export async function getReviewRecording(recordingId: string): Promise<ReviewRecordingSnapshot> {
  const response = await fetch(`/api/review-recordings/${encodeURIComponent(recordingId)}`)
  if (response.status === 404) {
    throw new ReviewRecordingNotFoundError()
  }
  return asJson(response)
}

export type MomentKind = 'INSIGHT' | 'QUESTION' | 'CONCERN' | 'DECISION' | 'ACTION' | 'VERIFICATION'
export type MomentStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED'

export interface Moment {
  momentId: string
  kind: MomentKind
  reference: string | null
  taggedAt: string
  status: MomentStatus
}

// Shared display label for each moment kind — used wherever a moment is rendered
// (ReviewRecordingControl's own timeline, and ReviewReplayTimeline's, ticket #211).
export const MOMENT_KINDS: { kind: MomentKind; label: string }[] = [
  { kind: 'INSIGHT', label: 'Insight' },
  { kind: 'QUESTION', label: 'Question' },
  { kind: 'CONCERN', label: 'Concern' },
  { kind: 'DECISION', label: 'Decision' },
  { kind: 'ACTION', label: 'Action' },
  { kind: 'VERIFICATION', label: 'Verification' },
]

export interface ReviewRecordingSummary {
  durationSeconds: number
  momentCountsByKind: Record<string, number>
}

export async function tagMoment(recordingId: string, kind: MomentKind): Promise<Moment> {
  const response = await postJson(`/api/review-recordings/${encodeURIComponent(recordingId)}/moments`, { kind })
  return asJson(response)
}

export async function fetchMoments(recordingId: string): Promise<Moment[]> {
  const response = await fetch(`/api/review-recordings/${encodeURIComponent(recordingId)}/moments`)
  return asJson(response)
}

export async function confirmMoment(recordingId: string, momentId: string): Promise<void> {
  const response = await postJson(
    `/api/review-recordings/${encodeURIComponent(recordingId)}/moments/${encodeURIComponent(momentId)}/confirm`,
  )
  if (!response.ok) {
    throw new Error(`Confirm moment request failed: ${response.status}`)
  }
}

export async function rejectMoment(recordingId: string, momentId: string): Promise<void> {
  const response = await postJson(
    `/api/review-recordings/${encodeURIComponent(recordingId)}/moments/${encodeURIComponent(momentId)}/reject`,
  )
  if (!response.ok) {
    throw new Error(`Reject moment request failed: ${response.status}`)
  }
}

export async function editMoment(recordingId: string, momentId: string, kind: MomentKind): Promise<void> {
  const response = await postJson(
    `/api/review-recordings/${encodeURIComponent(recordingId)}/moments/${encodeURIComponent(momentId)}/edit`,
    { kind },
  )
  if (!response.ok) {
    throw new Error(`Edit moment request failed: ${response.status}`)
  }
}

export async function fetchSummary(recordingId: string): Promise<ReviewRecordingSummary> {
  const response = await fetch(`/api/review-recordings/${encodeURIComponent(recordingId)}/summary`)
  return asJson(response)
}

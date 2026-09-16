// Fetch wrapper and pure timeline logic for Review Replay's semantic
// timeline (ticket #211) — mirrors reviewRecording.ts's own conventions
// (plain fetch, no client library). Fetches the persisted artifact's raw
// moments from the existing Review Recorder artifact endpoint (ticket
// #207) directly, rather than duplicating them onto the Replay endpoint
// (ticket #210) — that endpoint's own job is reference resolution, not
// timeline shaping.

import type { Moment } from './reviewRecording'

export type { Moment } from './reviewRecording'

export class ReviewRecordingArtifactNotFoundError extends Error {}

interface ReviewRecordingArtifactResponse {
  moments: Moment[]
}

export async function fetchReplayMoments(recordingId: string): Promise<Moment[]> {
  const response = await fetch(`/api/review-recordings/artifacts/${encodeURIComponent(recordingId)}`)
  if (response.status === 404) {
    throw new ReviewRecordingArtifactNotFoundError()
  }
  if (!response.ok) {
    throw new Error(`Review Replay artifact request failed: ${response.status}`)
  }
  const artifact = (await response.json()) as ReviewRecordingArtifactResponse
  return artifact.moments
}

export interface MomentGroup {
  /** The reference every moment in this group shares, or null for a group of un-referenced moments. */
  reference: string | null
  moments: Moment[]
}

/**
 * Groups `moments` (assumed already in chronological order, as the
 * artifact stores them) by shared reference — a question and a later
 * decision/insight about the same entity read as one thread rather than
 * disconnected rows, per the ticket's "question → investigation →
 * decision" requirement. A moment with no reference is its own
 * single-moment group; grouping is never inferred across different
 * references. Group order follows each group's first moment's position
 * in the timeline.
 */
export function groupMomentsByReference(moments: Moment[]): MomentGroup[] {
  const groups: MomentGroup[] = []
  const groupByReference = new Map<string, MomentGroup>()

  for (const moment of moments) {
    if (moment.reference === null) {
      groups.push({ reference: null, moments: [moment] })
      continue
    }
    const existing = groupByReference.get(moment.reference)
    if (existing) {
      existing.moments.push(moment)
    } else {
      const group: MomentGroup = { reference: moment.reference, moments: [moment] }
      groupByReference.set(moment.reference, group)
      groups.push(group)
    }
  }

  return groups
}

/** The moment immediately after `currentMomentId` in `moments`, or the same id if already last/absent. */
export function nextMomentId(moments: Moment[], currentMomentId: string): string {
  const index = moments.findIndex((moment) => moment.momentId === currentMomentId)
  if (index < 0 || index === moments.length - 1) {
    return currentMomentId
  }
  return moments[index + 1].momentId
}

/** The moment immediately before `currentMomentId` in `moments`, or the same id if already first/absent. */
export function previousMomentId(moments: Moment[], currentMomentId: string): string {
  const index = moments.findIndex((moment) => moment.momentId === currentMomentId)
  if (index <= 0) {
    return currentMomentId
  }
  return moments[index - 1].momentId
}

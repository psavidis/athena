// Thin fetch wrapper over the Review Briefing backend API (ticket #223)
// — mirrors reviewRecording.ts's own conventions (plain fetch, no client
// library), kept in its own module since this is a distinct feature area.

import { NoPullRequestSelectedError, NotConnectedError } from './api'

export interface BriefingItem {
  description: string
  entityReference: string | null
  module: string | null
}

export interface ReviewBriefing {
  changeSummary: BriefingItem | null
  focusAreas: BriefingItem[]
  uncertainties: BriefingItem[]
  questions: BriefingItem[]
  historicalContext: BriefingItem[]
  relevantKnowledge: BriefingItem[]
  recommendedStartingPoint: BriefingItem | null
}

// A 409 covers both "no PR selected" and "no AI provider configured" (the backend's own
// prerequisites for composing a briefing) — the overlay doesn't need to tell these apart, since
// either way there's simply nothing to show yet, so both surface the same way here.
export async function getReviewBriefing(): Promise<ReviewBriefing> {
  const response = await fetch('/api/review-briefings')
  if (response.status === 401) {
    throw new NotConnectedError()
  }
  if (response.status === 409) {
    throw new NoPullRequestSelectedError()
  }
  if (!response.ok) {
    throw new Error(`Review Briefing request failed: ${response.status}`)
  }
  return response.json() as Promise<ReviewBriefing>
}

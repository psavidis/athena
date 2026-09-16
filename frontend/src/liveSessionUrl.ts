// A Live Code Review Session's shareable link (ticket #158): unlike a
// shared PR link (shareUrl.ts), which just re-selects the same PR against
// the receiving user's own session, this URL identifies an actual shared,
// joinable session on the backend (see LiveReviewSessionRegistry) — opening
// it joins the same live session other participants are already in.

export function liveSessionPath(sessionId: string): string {
  return `/live/${sessionId}`
}

export function parseLiveSessionPath(pathname: string): string | null {
  const match = pathname.match(/^\/live\/([^/]+)$/)
  return match ? match[1] : null
}

// Thin fetch/SSE wrappers over the Live Code Review Session backend API
// (ticket #158) — mirrors api.ts's own conventions (plain fetch, no client
// library), kept in its own module since this is a distinct feature area,
// not a Diff/Review-selection concern.

export type ParticipantMode = 'FOLLOWING' | 'EXPLORING' | 'PRESENTING'

export interface CanvasFocus {
  zoomLevel: string
  selectedEntityId: string | null
  selectedChangeKey: string | null
  navigationContext: string | null
}

export interface LiveParticipant {
  participantId: string
  displayName: string
  connected: boolean
  mode: ParticipantMode
  personalFocus: CanvasFocus | null
}

export interface LiveReviewSessionSnapshot {
  sessionId: string
  revision: number
  repositoryFullName: string
  pullRequestNumber: number
  sharedFocus: CanvasFocus
  presenterId: string | null
  creatorId: string
  participants: LiveParticipant[]
  ended: boolean
}

export interface LiveSessionJoinResult {
  sessionId: string
  participantId: string
  snapshot: LiveReviewSessionSnapshot
}

export class NoReviewSelectedForLiveSessionError extends Error {}

export class LiveSessionNotFoundError extends Error {}

async function asJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`Live session request failed: ${response.status}`)
  }
  return response.json() as Promise<T>
}

function postJson(path: string, body: unknown): Promise<Response> {
  return fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function startLiveSession(displayName: string): Promise<LiveSessionJoinResult> {
  const response = await postJson('/api/live-sessions', { displayName })
  if (response.status === 409) {
    throw new NoReviewSelectedForLiveSessionError()
  }
  return asJson(response)
}

export async function joinLiveSession(
  sessionId: string,
  displayName: string,
  participantId?: string,
): Promise<LiveSessionJoinResult> {
  const response = await postJson(`/api/live-sessions/${encodeURIComponent(sessionId)}/join`, {
    displayName,
    participantId: participantId ?? null,
  })
  if (response.status === 404) {
    throw new LiveSessionNotFoundError()
  }
  return asJson(response)
}

export async function getLiveSessionSnapshot(sessionId: string): Promise<LiveReviewSessionSnapshot> {
  const response = await fetch(`/api/live-sessions/${encodeURIComponent(sessionId)}`)
  if (response.status === 404) {
    throw new LiveSessionNotFoundError()
  }
  return asJson(response)
}

export async function takeControl(sessionId: string, participantId: string): Promise<LiveReviewSessionSnapshot> {
  return asJson(await postJson(`/api/live-sessions/${encodeURIComponent(sessionId)}/take-control`, { participantId }))
}

export async function followSharedView(sessionId: string, participantId: string): Promise<LiveReviewSessionSnapshot> {
  return asJson(await postJson(`/api/live-sessions/${encodeURIComponent(sessionId)}/follow`, { participantId }))
}

export async function exploreIndependently(
  sessionId: string,
  participantId: string,
  focus: CanvasFocus,
): Promise<LiveReviewSessionSnapshot> {
  return asJson(
    await postJson(`/api/live-sessions/${encodeURIComponent(sessionId)}/explore`, { participantId, ...focus }),
  )
}

/** Only valid for the session's current presenter — moves the shared focus for every participant. */
export async function presentFocus(
  sessionId: string,
  participantId: string,
  focus: CanvasFocus,
): Promise<LiveReviewSessionSnapshot> {
  return asJson(
    await postJson(`/api/live-sessions/${encodeURIComponent(sessionId)}/focus`, { participantId, ...focus }),
  )
}

export async function leaveLiveSession(sessionId: string, participantId: string): Promise<void> {
  await postJson(`/api/live-sessions/${encodeURIComponent(sessionId)}/leave`, { participantId })
}

export async function endLiveSession(sessionId: string, participantId: string): Promise<void> {
  await postJson(`/api/live-sessions/${encodeURIComponent(sessionId)}/end`, { participantId })
}

export interface LiveComment {
  id: string
  author: string
  postedAt: string
  text: string
}

export async function getLiveComments(sessionId: string, canvasItemId?: string): Promise<LiveComment[]> {
  const query = canvasItemId ? `?canvasItemId=${encodeURIComponent(canvasItemId)}` : ''
  return asJson(await fetch(`/api/live-sessions/${encodeURIComponent(sessionId)}/comments${query}`))
}

export async function addLiveComment(
  sessionId: string,
  participantId: string,
  text: string,
  canvasItemId?: string,
): Promise<LiveComment[]> {
  return asJson(
    await postJson(`/api/live-sessions/${encodeURIComponent(sessionId)}/comments`, {
      participantId,
      canvasItemId: canvasItemId ?? null,
      text,
    }),
  )
}

/**
 * Subscribes to a session's real-time updates over SSE (native
 * {@link EventSource}, no client library needed). Returns an unsubscribe
 * function; the browser's own EventSource reconnects automatically on a
 * dropped connection (ticket #158's "reconnection after temporary network
 * interruptions"), each reconnect re-registering this participant and
 * receiving the latest snapshot immediately.
 */
export function subscribeToLiveSession(
  sessionId: string,
  participantId: string,
  onSnapshot: (snapshot: LiveReviewSessionSnapshot) => void,
): () => void {
  // Every real browser this app targets supports EventSource; this guard only
  // matters for a test environment (jsdom) that doesn't implement it, so a
  // test can render this component without stubbing out real-time transport
  // just to exercise everything else.
  if (typeof EventSource === 'undefined') {
    return () => {}
  }
  const url = `/api/live-sessions/${encodeURIComponent(sessionId)}/events?participantId=${encodeURIComponent(participantId)}`
  const source = new EventSource(url)
  source.addEventListener('snapshot', (event) => {
    onSnapshot(JSON.parse((event as MessageEvent).data))
  })
  return () => source.close()
}

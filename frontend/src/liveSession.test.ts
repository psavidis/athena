import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from './test/server'
import {
  addLiveComment,
  followSharedView,
  getLiveComments,
  getLiveSessionSnapshot,
  joinLiveSession,
  LiveSessionNotFoundError,
  NoReviewSelectedForLiveSessionError,
  presentFocus,
  startLiveSession,
  takeControl,
} from './liveSession'

const SNAPSHOT = {
  sessionId: 'session-1',
  revision: 1,
  repositoryFullName: 'acme/widgets',
  pullRequestNumber: 42,
  sharedFocus: { zoomLevel: 'OVERVIEW', selectedEntityId: null, selectedChangeKey: null, navigationContext: null },
  presenterId: 'participant-1',
  creatorId: 'participant-1',
  participants: [
    { participantId: 'participant-1', displayName: 'Petros', connected: true, mode: 'PRESENTING', personalFocus: null },
  ],
  ended: false,
}

describe('liveSession API client', () => {
  it('starts a session from the current review', async () => {
    server.use(
      http.post('/api/live-sessions', () =>
        HttpResponse.json({ sessionId: 'session-1', participantId: 'participant-1', snapshot: SNAPSHOT }),
      ),
    )

    const result = await startLiveSession('Petros')

    expect(result.sessionId).toBe('session-1')
    expect(result.participantId).toBe('participant-1')
    expect(result.snapshot.presenterId).toBe('participant-1')
  })

  it('throws when starting a session with no review selected', async () => {
    server.use(http.post('/api/live-sessions', () => new HttpResponse(null, { status: 409 })))

    await expect(startLiveSession('Petros')).rejects.toBeInstanceOf(NoReviewSelectedForLiveSessionError)
  })

  it('joins an existing session', async () => {
    server.use(
      http.post('/api/live-sessions/session-1/join', () =>
        HttpResponse.json({ sessionId: 'session-1', participantId: 'participant-2', snapshot: SNAPSHOT }),
      ),
    )

    const result = await joinLiveSession('session-1', 'Maria')

    expect(result.participantId).toBe('participant-2')
  })

  it('throws when joining an unknown session', async () => {
    server.use(http.post('/api/live-sessions/does-not-exist/join', () => new HttpResponse(null, { status: 404 })))

    await expect(joinLiveSession('does-not-exist', 'Maria')).rejects.toBeInstanceOf(LiveSessionNotFoundError)
  })

  it('fetches the current snapshot', async () => {
    server.use(http.get('/api/live-sessions/session-1', () => HttpResponse.json(SNAPSHOT)))

    const snapshot = await getLiveSessionSnapshot('session-1')

    expect(snapshot.sessionId).toBe('session-1')
  })

  it('takes control of the shared presentation', async () => {
    server.use(
      http.post('/api/live-sessions/session-1/take-control', () =>
        HttpResponse.json({ ...SNAPSHOT, presenterId: 'participant-2' }),
      ),
    )

    const snapshot = await takeControl('session-1', 'participant-2')

    expect(snapshot.presenterId).toBe('participant-2')
  })

  it('follows the shared view', async () => {
    server.use(http.post('/api/live-sessions/session-1/follow', () => HttpResponse.json(SNAPSHOT)))

    await expect(followSharedView('session-1', 'participant-2')).resolves.toEqual(SNAPSHOT)
  })

  it('moves the shared focus as presenter', async () => {
    let requestBody: unknown
    server.use(
      http.post('/api/live-sessions/session-1/focus', async ({ request }) => {
        requestBody = await request.json()
        return HttpResponse.json(SNAPSHOT)
      }),
    )

    await presentFocus('session-1', 'participant-1', {
      zoomLevel: 'ARCHITECTURE',
      selectedEntityId: 'component:PaymentValidator',
      selectedChangeKey: null,
      navigationContext: 'PaymentValidator',
    })

    expect(requestBody).toEqual({
      participantId: 'participant-1',
      zoomLevel: 'ARCHITECTURE',
      selectedEntityId: 'component:PaymentValidator',
      selectedChangeKey: null,
      navigationContext: 'PaymentValidator',
    })
  })

  it('posts and lists collaborative comments scoped to a canvas item', async () => {
    server.use(
      http.post('/api/live-sessions/session-1/comments', () =>
        HttpResponse.json([{ id: 'c1', author: 'Petros', postedAt: '2024-01-01T00:00:00Z', text: 'Looks good' }]),
      ),
      http.get('/api/live-sessions/session-1/comments', () =>
        HttpResponse.json([{ id: 'c1', author: 'Petros', postedAt: '2024-01-01T00:00:00Z', text: 'Looks good' }]),
      ),
    )

    const posted = await addLiveComment('session-1', 'participant-1', 'Looks good', 'component:PaymentValidator')
    const listed = await getLiveComments('session-1', 'component:PaymentValidator')

    expect(posted).toHaveLength(1)
    expect(listed[0].text).toBe('Looks good')
  })
})

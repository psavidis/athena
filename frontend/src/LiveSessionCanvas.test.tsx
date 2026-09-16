import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LiveSessionCanvas from './LiveSessionCanvas'
import type { ModuleTopology } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/live_code_review_session_panel.feature

const EMPTY_TOPOLOGY: ModuleTopology = { territories: [], dependencies: [] }

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

function renderCanvas() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <LiveSessionCanvas
        pullRequest={{ number: 42, title: 'Add live module', author: 'octocat', baseRevision: 'a', headRevision: 'b' }}
        onNotConnected={vi.fn()}
        onNoPullRequestSelected={vi.fn()}
      />
    </QueryClientProvider>,
  )
}

function mockEndpoints() {
  server.use(
    http.get('/api/review/topology', () => HttpResponse.json(EMPTY_TOPOLOGY)),
    http.get('/api/review/canvas-items/comment-counts', () => HttpResponse.json({})),
    http.post('/api/live-sessions', () =>
      HttpResponse.json({ sessionId: 'session-1', participantId: 'participant-1', snapshot: SNAPSHOT }),
    ),
    // The presenter's own territory navigation is pushed here (ticket #158) —
    // fires once on becoming presenter even with no territory focused yet
    // (reports the neutral "OVERVIEW" focus), so every test that starts a
    // session needs this mocked even when it never dives into a territory.
    http.post('/api/live-sessions/session-1/focus', () => HttpResponse.json(SNAPSHOT)),
  )
}

async function startSession(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Start Live Review Session' }))
  await user.type(screen.getByLabelText('Your name'), 'Petros')
  await user.click(screen.getByRole('button', { name: 'Start' }))
  await screen.findByTitle(/Petros \(presenting\)/)
}

describe('Live Code Review Session panel', () => {
  // Starting a session rewrites the URL to its shareable /live/:id link
  // (window.history.replaceState) — reset it before every test so one
  // test's session id never leaks into the next test's fresh render as a
  // spurious "join via link" prompt.
  beforeEach(() => {
    window.history.replaceState(null, '', '/')
  })

  it('starting a session shows the reviewer as the sole, presenting participant', async () => {
    mockEndpoints()
    renderCanvas()
    const user = userEvent.setup()

    await startSession(user)

    expect(screen.getByTitle(/Petros \(presenting\)/)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Copy share link' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'End session' })).toBeVisible()
  })

  it('a reviewer posts a session comment and sees it listed', async () => {
    mockEndpoints()
    server.use(
      http.get('/api/live-sessions/session-1/comments', () => HttpResponse.json([])),
      http.post('/api/live-sessions/session-1/comments', () =>
        HttpResponse.json([{ id: 'c1', author: 'Petros', postedAt: '2024-01-01T00:00:00Z', text: 'Looks good' }]),
      ),
    )
    renderCanvas()
    const user = userEvent.setup()
    await startSession(user)

    await user.click(screen.getByRole('button', { name: 'Session comments' }))
    await user.type(screen.getByLabelText('Comment'), 'Looks good')
    await user.click(screen.getByRole('button', { name: 'Post' }))

    expect(await screen.findByText('Looks good')).toBeVisible()
  })
})

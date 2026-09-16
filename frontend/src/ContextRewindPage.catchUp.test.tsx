import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import ContextRewindPage from './ContextRewindPage'
import type { ContextRewind } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/context_rewind_catch_up.feature

function renderContextRewindPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ContextRewindPage entityName="PaymentProcessor" onBack={vi.fn()} />
    </QueryClientProvider>,
  )
}

const FULL_HISTORY: ContextRewind = {
  entityName: 'PaymentProcessor',
  evolutionTimeline: [
    { description: 'PaymentProcessor introduced', occurredAt: '2025-12-01T00:00:00Z' },
    { description: 'Retry mechanism added', occurredAt: '2026-02-01T00:00:00Z' },
  ],
  pullRequestReferences: [],
  aiNarrative: null,
  insufficientHistoryMessage: null,
  knowledgeFacts: [],
}

/** Distinguishes an unscoped request from one scoped by `?since=`, the way the real backend
 * does — so a test genuinely fails (red) if the frontend never sends `since`, rather than
 * silently matching regardless of scoping. */
function mockScopedContextRewind(scopedBody: ContextRewind) {
  server.use(
    http.get('/api/review/context-rewind/PaymentProcessor', ({ request }) => {
      const since = new URL(request.url).searchParams.get('since')
      return HttpResponse.json(since ? scopedBody : FULL_HISTORY)
    }),
  )
}

describe('Context Rewind — "Catch me up" since a chosen date', () => {
  it('shows only the activity after the chosen date, with a count', async () => {
    // Given a commit on "2025-12-01" changed "PaymentProcessor"
    // And a commit on "2026-02-01" separately changed "PaymentProcessor"
    mockScopedContextRewind({
      ...FULL_HISTORY,
      evolutionTimeline: [{ description: 'Retry mechanism added', occurredAt: '2026-02-01T00:00:00Z' }],
    })
    renderContextRewindPage()
    const user = userEvent.setup()
    await screen.findByLabelText('Catch me up since')

    // When the developer asks Context Rewind to catch them up on "PaymentProcessor" since "2026-01-01"
    await user.type(screen.getByLabelText('Catch me up since'), '2026-01-01')
    await user.click(screen.getByRole('button', { name: 'Catch me up' }))

    // Then Context Rewind shows only the "2026-02-01" activity
    const events = await screen.findAllByTestId('timeline-event')
    expect(events).toHaveLength(1)
    expect(events[0]).toHaveTextContent('Retry mechanism added')
    // And it reports "1" activity since "2026-01-01"
    expect(screen.getByText('1 change since 2026-01-01')).toBeVisible()
  })

  it('shows both events when the developer opens Context Rewind without catching up', async () => {
    // Given a commit on "2025-12-01" changed "PaymentProcessor"
    // And a commit on "2026-02-01" separately changed "PaymentProcessor"
    mockScopedContextRewind(FULL_HISTORY)
    renderContextRewindPage()
    const user = userEvent.setup()

    // When the developer opens Context Rewind for "PaymentProcessor" without catching up
    await user.click(await screen.findByRole('button', { name: 'Zoom in' }))

    // Then Context Rewind shows both the "2025-12-01" and "2026-02-01" activity
    expect(await screen.findAllByTestId('timeline-event')).toHaveLength(2)
  })

  it('states plainly when nothing has changed since the chosen date', async () => {
    // Given a commit on "2025-12-01" changed "PaymentProcessor"
    mockScopedContextRewind({ ...FULL_HISTORY, evolutionTimeline: [] })
    renderContextRewindPage()
    const user = userEvent.setup()
    await screen.findByLabelText('Catch me up since')

    // When the developer asks Context Rewind to catch them up on "PaymentProcessor" since "2026-06-01"
    await user.type(screen.getByLabelText('Catch me up since'), '2026-06-01')
    await user.click(screen.getByRole('button', { name: 'Catch me up' }))

    // Then Context Rewind states that nothing has changed since "2026-06-01"
    expect(await screen.findByText('Nothing has changed since 2026-06-01.')).toBeVisible()
    // And it does not render an empty timeline
    expect(screen.queryAllByTestId('timeline-event')).toHaveLength(0)
  })

  it('disables "Catch me up" when the date is blank', async () => {
    // Given the developer is at Context Rewind's "catch me up" prompt for "PaymentProcessor"
    mockScopedContextRewind(FULL_HISTORY)
    renderContextRewindPage()
    await screen.findByLabelText('Catch me up since')

    // When the developer leaves the date blank
    // Then the "Catch me up" action is disabled
    expect(screen.getByRole('button', { name: 'Catch me up' })).toBeDisabled()
  })
})

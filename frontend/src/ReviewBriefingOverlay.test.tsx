import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import ReviewBriefingOverlay from './ReviewBriefingOverlay'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/review_briefing_overlay.feature

const BRIEFING = {
  changeSummary: { description: 'Renamed a method on Greeter.', entityReference: 'Greeter' },
  focusAreas: [{ description: 'The retry logic changed', entityReference: 'Greeter' }],
  uncertainties: [],
  questions: [],
  historicalContext: [],
  relevantKnowledge: [],
  recommendedStartingPoint: null,
}

function mockBriefing() {
  server.use(http.get('/api/review-briefings', () => HttpResponse.json(BRIEFING)))
}

/** Mirrors how SemanticCanvasPage would own open/collapsed state per PR (ticket #223) —
 * a small harness so the overlay's own behavior can be exercised without the whole canvas page. */
function Harness({ pullRequestNumber }: { pullRequestNumber: number }) {
  const [collapsed, setCollapsed] = useState(false)
  return (
    <div key={pullRequestNumber}>
      <ReviewBriefingOverlay
        collapsed={collapsed}
        onStartReview={() => setCollapsed(true)}
        onReopen={() => setCollapsed(false)}
      />
    </div>
  )
}

function renderHarness(pullRequestNumber = 42) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <Harness pullRequestNumber={pullRequestNumber} />
    </QueryClientProvider>,
  )
}

describe('Review Briefing overlay', () => {
  it('a developer sees the briefing overlay on first entering a PR', async () => {
    mockBriefing()
    renderHarness()

    expect(await screen.findByText('Review Briefing')).toBeVisible()
    expect(await screen.findByText('Renamed a method on Greeter.')).toBeVisible()
  })

  it('starting the review collapses the overlay to an indicator', async () => {
    mockBriefing()
    renderHarness()
    const user = userEvent.setup()
    await screen.findByText('Renamed a method on Greeter.')

    await user.click(screen.getByRole('button', { name: 'Start Review' }))

    expect(screen.queryByText('Review Briefing')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Briefing' })).toBeVisible()
  })

  it('the collapsed indicator reopens the overlay', async () => {
    mockBriefing()
    renderHarness()
    const user = userEvent.setup()
    await screen.findByText('Renamed a method on Greeter.')
    await user.click(screen.getByRole('button', { name: 'Start Review' }))

    await user.click(screen.getByRole('button', { name: 'Briefing' }))

    expect(await screen.findByText('Review Briefing')).toBeVisible()
  })
})

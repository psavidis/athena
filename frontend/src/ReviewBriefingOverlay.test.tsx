import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import ReviewBriefingOverlay from './ReviewBriefingOverlay'
import { server } from './test/server'
import type { ReviewBriefing } from './reviewBriefing'

// Traces frontend/src/test/resources/features/ui_first_experience/review_briefing_overlay.feature

const BRIEFING: ReviewBriefing = {
  changeSummary: { description: 'Renamed a method on Greeter.', entityReference: 'Greeter', module: null },
  focusAreas: [{ description: 'The retry logic changed', entityReference: 'Greeter', module: null }],
  uncertainties: [],
  questions: [],
  historicalContext: [],
  relevantKnowledge: [],
  recommendedStartingPoint: null,
}

function mockBriefing(briefing: ReviewBriefing = BRIEFING) {
  server.use(http.get('/api/review-briefings', () => HttpResponse.json(briefing)))
}

/** Mirrors how SemanticCanvasPage would own open/collapsed state per PR (ticket #223) —
 * a small harness so the overlay's own behavior can be exercised without the whole canvas page. */
function Harness({
  pullRequestNumber,
  onFocusModule,
}: {
  pullRequestNumber: number
  onFocusModule?: (moduleName: string) => void
}) {
  const [collapsed, setCollapsed] = useState(false)
  return (
    <ReviewBriefingOverlay
      pullRequestNumber={pullRequestNumber}
      collapsed={collapsed}
      onStartReview={() => setCollapsed(true)}
      onReopen={() => setCollapsed(false)}
      onFocusModule={onFocusModule}
    />
  )
}

function renderHarness(pullRequestNumber = 42, onFocusModule?: (moduleName: string) => void) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <Harness pullRequestNumber={pullRequestNumber} onFocusModule={onFocusModule} />
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

// Traces frontend/src/test/resources/features/ui_first_experience/review_briefing_canvas_navigation.feature
describe('Review Briefing — Semantic Canvas navigation', () => {
  it("selecting a briefing item focuses the canvas on the module its entity belongs to", async () => {
    mockBriefing({
      ...BRIEFING,
      focusAreas: [{ description: 'The retry logic changed', entityReference: 'OrderService', module: 'orders' }],
    })
    const onFocusModule = vi.fn()
    renderHarness(42, onFocusModule)
    const user = userEvent.setup()
    const item = await screen.findByRole('button', { name: 'The retry logic changed' })

    await user.click(item)

    expect(onFocusModule).toHaveBeenCalledWith('orders')
  })

  it("selecting a briefing item whose entity's module can't be determined leaves the canvas unchanged", async () => {
    mockBriefing({
      ...BRIEFING,
      focusAreas: [],
      questions: [{ description: 'Why was this retried?', entityReference: 'UnknownEntity', module: null }],
    })
    const onFocusModule = vi.fn()
    renderHarness(42, onFocusModule)
    const user = userEvent.setup()
    const item = await screen.findByRole('button', { name: 'Why was this retried?' })

    await user.click(item)

    expect(onFocusModule).not.toHaveBeenCalled()
  })

  it('selecting a briefing item with no entity reference leaves the canvas unchanged', async () => {
    mockBriefing({
      ...BRIEFING,
      focusAreas: [],
      relevantKnowledge: [{ description: 'This repo uses a monorepo layout.', entityReference: null, module: null }],
    })
    const onFocusModule = vi.fn()
    renderHarness(42, onFocusModule)
    const user = userEvent.setup()
    const item = await screen.findByRole('button', { name: 'This repo uses a monorepo layout.' })

    await user.click(item)

    expect(onFocusModule).not.toHaveBeenCalled()
  })

  it('starting the review jumps the canvas to the recommended starting point', async () => {
    mockBriefing({
      ...BRIEFING,
      focusAreas: [],
      recommendedStartingPoint: { description: 'Start with PaymentGateway', entityReference: 'PaymentGateway', module: 'payments' },
    })
    const onFocusModule = vi.fn()
    renderHarness(42, onFocusModule)
    const user = userEvent.setup()
    await screen.findByText('Renamed a method on Greeter.')

    await user.click(screen.getByRole('button', { name: 'Start Review' }))

    expect(onFocusModule).toHaveBeenCalledWith('payments')
    expect(screen.queryByText('Review Briefing')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Briefing' })).toBeVisible()
  })

  it('starting the review with no recommended starting point leaves the canvas unchanged', async () => {
    mockBriefing({ ...BRIEFING, focusAreas: [], recommendedStartingPoint: null })
    const onFocusModule = vi.fn()
    renderHarness(42, onFocusModule)
    const user = userEvent.setup()
    await screen.findByText('Renamed a method on Greeter.')

    await user.click(screen.getByRole('button', { name: 'Start Review' }))

    expect(onFocusModule).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Briefing' })).toBeVisible()
  })
})

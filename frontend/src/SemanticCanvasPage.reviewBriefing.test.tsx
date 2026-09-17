import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ImportedPullRequest, ModuleTopology } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/review_briefing_overlay.feature

const EMPTY_TOPOLOGY: ModuleTopology = { territories: [], dependencies: [] }

function briefingFor(summary: string) {
  return {
    changeSummary: { description: summary, entityReference: 'Greeter' },
    focusAreas: [],
    uncertainties: [],
    questions: [],
    historicalContext: [],
    relevantKnowledge: [],
    recommendedStartingPoint: null,
  }
}

function mockEndpoints() {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(EMPTY_TOPOLOGY)))
}

// The real endpoint has no PR number in its URL — it reads the session's currently-selected PR
// server-side — so a test can't tell PRs apart by request shape. Instead, this changes what the
// *same* endpoint returns between renders, simulating "the backend's selected PR changed
// server-side" the way a real PR switch would: if the frontend's query key isn't scoped to the
// PR, it'll keep showing the first response instead of re-fetching this new one.
function mockBriefing(summary: string) {
  server.use(http.get('/api/review-briefings', () => HttpResponse.json(briefingFor(summary))))
}

function pr(number: number): ImportedPullRequest {
  return { number, title: `PR ${number}`, author: 'octocat', baseRevision: 'base', headRevision: 'head' }
}

function renderCanvas(pullRequest: ImportedPullRequest | null) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const { rerender } = render(
    <QueryClientProvider client={queryClient}>
      <SemanticCanvasPage pullRequest={pullRequest} onNotConnected={vi.fn()} onNoPullRequestSelected={vi.fn()} />
    </QueryClientProvider>,
  )
  return {
    rerenderWithPr(next: ImportedPullRequest) {
      rerender(
        <QueryClientProvider client={queryClient}>
          <SemanticCanvasPage pullRequest={next} onNotConnected={vi.fn()} onNoPullRequestSelected={vi.fn()} />
        </QueryClientProvider>,
      )
    },
  }
}

describe('SemanticCanvasPage Review Briefing overlay', () => {
  it('shows the briefing overlay on first entering a PR', async () => {
    mockEndpoints()
    mockBriefing('Renamed a method on Greeter.')
    renderCanvas(pr(42))

    expect(await screen.findByText('Review Briefing')).toBeVisible()
    expect(await screen.findByText('Renamed a method on Greeter.')).toBeVisible()
  })

  it('a different PR shows the overlay again on entry, with that PR\'s own content', async () => {
    mockEndpoints()
    mockBriefing('Renamed a method on Greeter.')
    const { rerenderWithPr } = renderCanvas(pr(42))
    const user = userEvent.setup()
    await screen.findByText('Renamed a method on Greeter.')
    await user.click(screen.getByRole('button', { name: 'Start Review' }))
    expect(screen.queryByText('Review Briefing')).not.toBeInTheDocument()

    mockBriefing('Extracted a helper on OrderService.')
    rerenderWithPr(pr(43))

    expect(await screen.findByText('Review Briefing')).toBeVisible()
    expect(await screen.findByText('Extracted a helper on OrderService.')).toBeVisible()
    expect(screen.queryByText('Renamed a method on Greeter.')).not.toBeInTheDocument()
  })
})

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

const BRIEFING = {
  changeSummary: { description: 'Renamed a method on Greeter.', entityReference: 'Greeter' },
  focusAreas: [],
  uncertainties: [],
  questions: [],
  historicalContext: [],
  relevantKnowledge: [],
  recommendedStartingPoint: null,
}

function mockEndpoints() {
  server.use(
    http.get('/api/review/topology', () => HttpResponse.json(EMPTY_TOPOLOGY)),
    http.get('/api/review-briefings', () => HttpResponse.json(BRIEFING)),
  )
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
    renderCanvas(pr(42))

    expect(await screen.findByText('Review Briefing')).toBeVisible()
  })

  it('a different PR shows the overlay again on entry', async () => {
    mockEndpoints()
    const { rerenderWithPr } = renderCanvas(pr(42))
    const user = userEvent.setup()
    await screen.findByText('Review Briefing')
    await user.click(screen.getByRole('button', { name: 'Start Review' }))
    expect(screen.queryByText('Review Briefing')).not.toBeInTheDocument()

    rerenderWithPr(pr(43))

    expect(await screen.findByText('Review Briefing')).toBeVisible()
  })
})

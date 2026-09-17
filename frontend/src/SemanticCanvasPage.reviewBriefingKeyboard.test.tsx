import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ImportedPullRequest, ModuleTopology, SemanticProfile } from './api'
import type { ReviewBriefing } from './reviewBriefing'
import { server } from './test/server'
import { pressShortcut } from './test/keyboardShortcuts'

// Traces frontend/src/test/resources/features/ui_first_experience/review_briefing_keyboard_interaction.feature

const EMPTY_TOPOLOGY: ModuleTopology = { territories: [], dependencies: [] }

const ORDERS_PROFILE: SemanticProfile = {
  dimensions: [
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Retry logic',
      conceptDescription: 'Description',
      inferred: false,
      confidencePercent: 100,
      evidence: [],
      supportingConceptNames: [],
      filesTouched: ['OrderService.java'],
    },
  ],
}

const BASE_BRIEFING: ReviewBriefing = {
  changeSummary: null,
  focusAreas: [],
  uncertainties: [],
  questions: [],
  historicalContext: [],
  relevantKnowledge: [],
  recommendedStartingPoint: null,
}

function mockEndpoints() {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(EMPTY_TOPOLOGY)))
}

function mockModuleProfile(moduleName: string, profile: SemanticProfile) {
  server.use(http.get(`/api/review/modules/${moduleName}/semantic-profile`, () => HttpResponse.json(profile)))
}

function mockBriefing(briefing: Partial<ReviewBriefing>) {
  server.use(http.get('/api/review-briefings', () => HttpResponse.json({ ...BASE_BRIEFING, ...briefing })))
}

function pr(number: number): ImportedPullRequest {
  return { number, title: `PR ${number}`, author: 'octocat', baseRevision: 'base', headRevision: 'head' }
}

function renderCanvas() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticCanvasPage pullRequest={pr(42)} onNotConnected={vi.fn()} onNoPullRequestSelected={vi.fn()} />
    </QueryClientProvider>,
  )
}

describe('Review Briefing — keyboard-first interaction', () => {
  it('a developer reopens the collapsed indicator with a keyboard shortcut', async () => {
    mockEndpoints()
    mockBriefing({})
    renderCanvas()
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Start Review' })
    await user.click(screen.getByRole('button', { name: 'Start Review' }))
    const indicator = screen.getByRole('button', { name: 'Briefing' })
    indicator.focus()

    pressShortcut('enterItem', indicator)

    expect(await screen.findByText('Review Briefing')).toBeVisible()
  })

  it('a developer closes the overlay with a keyboard shortcut', async () => {
    mockEndpoints()
    mockBriefing({})
    renderCanvas()
    await screen.findByRole('button', { name: 'Start Review' })

    pressShortcut('leaveRegion')

    expect(screen.queryByText('Review Briefing')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Briefing' })).toBeVisible()
  })

  it('a developer moves focus to the next briefing item', async () => {
    mockEndpoints()
    mockBriefing({
      focusAreas: [{ description: 'The retry logic changed', entityReference: null, module: null }],
      questions: [{ description: 'Why was this retried?', entityReference: null, module: null }],
    })
    renderCanvas()
    const focusArea = await screen.findByRole('button', { name: 'The retry logic changed' })
    focusArea.focus()

    pressShortcut('nextItem', focusArea)

    expect(screen.getByRole('button', { name: 'Why was this retried?' })).toHaveFocus()
  })

  it('a developer moves focus to the previous briefing item', async () => {
    mockEndpoints()
    mockBriefing({
      focusAreas: [{ description: 'The retry logic changed', entityReference: null, module: null }],
      questions: [{ description: 'Why was this retried?', entityReference: null, module: null }],
    })
    renderCanvas()
    const question = await screen.findByRole('button', { name: 'Why was this retried?' })
    question.focus()

    pressShortcut('previousItem', question)

    expect(screen.getByRole('button', { name: 'The retry logic changed' })).toHaveFocus()
  })

  it('a developer selects the focused item with a keyboard shortcut', async () => {
    mockEndpoints()
    mockModuleProfile('orders', ORDERS_PROFILE)
    mockBriefing({
      focusAreas: [{ description: 'The retry logic changed', entityReference: 'OrderService', module: 'orders' }],
    })
    renderCanvas()
    const focusArea = await screen.findByRole('button', { name: 'The retry logic changed' })
    focusArea.focus()

    pressShortcut('enterItem', focusArea)

    await screen.findAllByTestId('file-node')
  })

  it('a developer starts the review with a keyboard shortcut', async () => {
    mockEndpoints()
    mockBriefing({})
    renderCanvas()
    const startReview = await screen.findByRole('button', { name: 'Start Review' })
    startReview.focus()

    pressShortcut('enterItem', startReview)

    expect(screen.queryByText('Review Briefing')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Briefing' })).toBeVisible()
  })
})

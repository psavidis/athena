import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import ContextRewindPage from './ContextRewindPage'
import type { ContextRewind } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/context_rewind_story_timeline.feature

function renderContextRewindPage(entityName = 'PaymentProcessor') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onBack = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <ContextRewindPage entityName={entityName} onBack={onBack} />
    </QueryClientProvider>,
  )
  return { onBack }
}

function mockContextRewind(entityName: string, body: ContextRewind) {
  server.use(http.get(`/api/review/context-rewind/${entityName}`, () => HttpResponse.json(body)))
}

function mockContextRewindStatus(entityName: string, status: number) {
  server.use(http.get(`/api/review/context-rewind/${entityName}`, () => new HttpResponse(null, { status })))
}

/** Context Rewind now lands at the Orientation level (ticket #188); these Overview-level
 * scenarios need an explicit zoom-in first. */
async function zoomToOverview() {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Zoom in' }))
  return user
}

const TIMELINE_ENTITY: ContextRewind = {
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

describe('Context Rewind — story timeline & evidence panel', () => {
  it("shows the entity's evolution timeline oldest to newest, ending at the current state", async () => {
    // Given a pull request is selected whose repository's history for "PaymentProcessor" includes a
    // "2025-12-01" change and a "2026-02-01" change
    mockContextRewind('PaymentProcessor', TIMELINE_ENTITY)

    // When the developer opens Context Rewind for "PaymentProcessor" and zooms in to the Overview level
    renderContextRewindPage('PaymentProcessor')
    await zoomToOverview()

    // Then the timeline lists the "2025-12-01" event before the "2026-02-01" event
    const events = await screen.findAllByTestId('timeline-event')
    expect(events.map((event) => event.textContent)).toEqual([
      expect.stringContaining('PaymentProcessor introduced'),
      expect.stringContaining('Retry mechanism added'),
    ])
    // And the timeline ends at "PaymentProcessor"'s current state
    expect(screen.getByText('Current state')).toBeVisible()
  })

  it('reveals the evidence behind a selected timeline event', async () => {
    // Given the developer has Context Rewind open for "PaymentProcessor" showing a "Retry mechanism added" event
    mockContextRewind('PaymentProcessor', TIMELINE_ENTITY)
    renderContextRewindPage('PaymentProcessor')
    const user = await zoomToOverview()

    // When the developer selects the "Retry mechanism added" event
    await user.click(screen.getByText('Retry mechanism added'))

    // Then the evidence panel shows that event's description and when it happened
    const evidence = screen.getByTestId('evidence-panel')
    expect(evidence).toHaveTextContent('Retry mechanism added')
    expect(evidence).toHaveTextContent('2026-02-01')
  })

  it('shows Pull Requests that touched the entity as navigable evidence', async () => {
    // Given a pull request 217 added retry handling to "PaymentProcessor"
    mockContextRewind('PaymentProcessor', {
      ...TIMELINE_ENTITY,
      pullRequestReferences: [{ number: 217, repositoryFullName: 'acme/widgets', url: 'https://github.com/acme/widgets/pull/217' }],
    })

    // When the developer opens Context Rewind for "PaymentProcessor" and zooms in to the Overview level
    renderContextRewindPage('PaymentProcessor')
    await zoomToOverview()

    // Then Context Rewind references Pull Request 217
    const link = await screen.findByRole('link', { name: /Pull Request 217/ })
    // And that reference can be opened directly
    expect(link).toHaveAttribute('href', 'https://github.com/acme/widgets/pull/217')
  })

  it('shows an AI-generated narrative clearly labeled as an interpretation, not a project fact', async () => {
    // Given "PaymentProcessor" has enough history for Athena to generate a narrative about it
    mockContextRewind('PaymentProcessor', {
      ...TIMELINE_ENTITY,
      aiNarrative: 'This component was separated from OrderService to isolate payment provider logic.',
    })

    // When the developer opens Context Rewind for "PaymentProcessor" and zooms in to the Overview level
    renderContextRewindPage('PaymentProcessor')
    await zoomToOverview()

    // Then Context Rewind shows the AI-generated narrative
    expect(
      await screen.findByText('This component was separated from OrderService to isolate payment provider logic.'),
    ).toBeVisible()
    // And it is visually labeled as an AI-generated interpretation, not a project fact
    expect(screen.getByText('AI-generated interpretation')).toBeVisible()
  })

  it('states plainly when there is not enough history, instead of rendering an empty timeline', async () => {
    // Given "UnknownWidget" has no recorded history
    mockContextRewind('UnknownWidget', {
      entityName: 'UnknownWidget',
      evolutionTimeline: [],
      pullRequestReferences: [],
      aiNarrative: null,
      insufficientHistoryMessage: 'Not enough historical information is available for UnknownWidget',
      knowledgeFacts: [],
    })

    // When the developer opens Context Rewind for "UnknownWidget"
    renderContextRewindPage('UnknownWidget')

    // Then Context Rewind states that not enough historical information is available for "UnknownWidget"
    expect(await screen.findByText('Not enough historical information is available for UnknownWidget')).toBeVisible()
    // And it does not render an empty timeline
    expect(screen.queryAllByTestId('timeline-event')).toHaveLength(0)
  })

  it('tells the developer to select a pull request first when none is currently selected', async () => {
    // Given no pull request is currently selected
    mockContextRewindStatus('PaymentProcessor', 409)

    // When the developer tries to open Context Rewind for "PaymentProcessor"
    renderContextRewindPage('PaymentProcessor')

    // Then Athena tells the developer to select a pull request first
    expect(await screen.findByText('Select a pull request first to use Context Rewind.')).toBeVisible()
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import ContextRewindPage from './ContextRewindPage'
import type { ContextRewind } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/context_rewind_semantic_zoom.feature

function renderContextRewindPage(props: { moduleName?: string; conceptName?: string } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ContextRewindPage entityName="PaymentProcessor" onBack={vi.fn()} {...props} />
    </QueryClientProvider>,
  )
}

function mockContextRewind(entityName: string, body: ContextRewind) {
  server.use(http.get(`/api/review/context-rewind/${entityName}`, () => HttpResponse.json(body)))
}

const TIMELINE_ENTITY: ContextRewind = {
  entityName: 'PaymentProcessor',
  evolutionTimeline: [
    { description: 'PaymentProcessor introduced', occurredAt: '2025-12-01T00:00:00Z' },
    { description: 'Retry mechanism added', occurredAt: '2026-02-01T00:00:00Z' },
  ],
  pullRequestReferences: [{ number: 217, repositoryFullName: 'acme/widgets', url: 'https://github.com/acme/widgets/pull/217' }],
  aiNarrative: null,
  insufficientHistoryMessage: null,
}

describe('Context Rewind — semantic zoom for history', () => {
  it('lands at the Orientation level, showing only where the entity sits', async () => {
    // Given the developer opens Context Rewind for "PaymentProcessor" reached from the "Payment processing"
    // concept in the "crowdness-live" module
    mockContextRewind('PaymentProcessor', TIMELINE_ENTITY)

    renderContextRewindPage({ moduleName: 'crowdness-live', conceptName: 'Payment processing' })

    // Then Context Rewind shows "crowdness-live › Payment processing › PaymentProcessor"
    expect(await screen.findByText('crowdness-live › Payment processing › PaymentProcessor')).toBeVisible()
    // And it does not show the evolution timeline yet
    expect(screen.queryAllByTestId('timeline-event')).toHaveLength(0)
  })

  it('shows just the entity name when no location breadcrumb is available', async () => {
    // Given the developer opens Context Rewind for "PaymentProcessor" with no known module or concept
    mockContextRewind('PaymentProcessor', TIMELINE_ENTITY)

    renderContextRewindPage()

    // Then Context Rewind shows "PaymentProcessor" at the Orientation level
    expect(await screen.findByText('PaymentProcessor')).toBeVisible()
    expect(screen.queryAllByTestId('timeline-event')).toHaveLength(0)
  })

  it('reveals the Overview — timeline and Pull Requests — when zoomed in from Orientation', async () => {
    // Given the developer has Context Rewind open for "PaymentProcessor" at the Orientation level
    mockContextRewind('PaymentProcessor', TIMELINE_ENTITY)
    renderContextRewindPage()
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Zoom in' })

    // When the developer zooms in
    await user.click(screen.getByRole('button', { name: 'Zoom in' }))

    // Then Context Rewind shows "PaymentProcessor"'s evolution timeline
    expect(await screen.findAllByTestId('timeline-event')).toHaveLength(2)
    // And it shows the Pull Requests that touched "PaymentProcessor"
    expect(screen.getByRole('link', { name: /Pull Request 217/ })).toBeVisible()
  })

  it('reveals the Detail for a selected timeline event, focused solely on that event', async () => {
    // Given the developer has Context Rewind open for "PaymentProcessor" at the Overview level
    // with the "Retry mechanism added" event selected
    mockContextRewind('PaymentProcessor', TIMELINE_ENTITY)
    renderContextRewindPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Zoom in' }))
    await user.click(await screen.findByText('Retry mechanism added'))

    // When the developer zooms in
    await user.click(screen.getByRole('button', { name: 'Zoom in' }))

    // Then Context Rewind shows only "Retry mechanism added"'s own evidence, full width
    expect(screen.getByTestId('evidence-panel')).toHaveTextContent('Retry mechanism added')
    // And it no longer shows the rest of the timeline
    expect(screen.queryAllByTestId('timeline-event')).toHaveLength(0)
  })

  it('returns to Overview with the same event still selected when zoomed out from Detail', async () => {
    // Given the developer has Context Rewind open for "PaymentProcessor" at the Detail level
    // for the "Retry mechanism added" event
    mockContextRewind('PaymentProcessor', TIMELINE_ENTITY)
    renderContextRewindPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Zoom in' }))
    await user.click(await screen.findByText('Retry mechanism added'))
    await user.click(screen.getByRole('button', { name: 'Zoom in' }))

    // When the developer zooms out
    await user.click(screen.getByRole('button', { name: 'Zoom out' }))

    // Then Context Rewind returns to the Overview level
    expect(await screen.findAllByTestId('timeline-event')).toHaveLength(2)
    // And the "Retry mechanism added" event is still selected
    expect(screen.getByTestId('evidence-panel')).toHaveTextContent('Retry mechanism added')
  })

  it('offers no "Zoom in" action from Overview when nothing is selected', async () => {
    // Given the developer has Context Rewind open for "PaymentProcessor" at the Overview level with nothing selected
    mockContextRewind('PaymentProcessor', TIMELINE_ENTITY)
    renderContextRewindPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Zoom in' }))
    await screen.findAllByTestId('timeline-event')

    // Then no "Zoom in" action is offered
    expect(screen.queryByRole('button', { name: 'Zoom in' })).not.toBeInTheDocument()
  })
})

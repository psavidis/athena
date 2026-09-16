import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import ContextRewindPage from './ContextRewindPage'
import type { ContextRewind } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/context_rewind_quick_actions.feature

function renderContextRewindPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ContextRewindPage entityName="PaymentProcessor" onBack={vi.fn()} />
    </QueryClientProvider>,
  )
}

function mockContextRewind(body: ContextRewind) {
  server.use(http.get('/api/review/context-rewind/PaymentProcessor', () => HttpResponse.json(body)))
}

const BASE: ContextRewind = {
  entityName: 'PaymentProcessor',
  evolutionTimeline: [],
  pullRequestReferences: [],
  aiNarrative: null,
  insufficientHistoryMessage: null,
  knowledgeFacts: [],
}

describe('Context Rewind — "Explain Why" quick actions', () => {
  it('shows the AI-generated narrative, evidence-cited, for a "Why" question', async () => {
    // Given "PaymentProcessor" has an AI-generated narrative and 2 recorded events and 1 Pull Request
    mockContextRewind({
      ...BASE,
      aiNarrative: 'This component was separated from OrderService to isolate payment provider logic.',
      evolutionTimeline: [
        { description: 'PaymentProcessor introduced', occurredAt: '2025-12-01T00:00:00Z' },
        { description: 'Retry mechanism added', occurredAt: '2026-02-01T00:00:00Z' },
      ],
      pullRequestReferences: [{ number: 217, repositoryFullName: 'acme/widgets', url: 'https://github.com/acme/widgets/pull/217' }],
    })
    renderContextRewindPage()
    const user = userEvent.setup()

    // When the developer asks "Why does this exist?"
    await user.click(await screen.findByRole('button', { name: 'Why does this exist?' }))

    // Then the answer shows the AI-generated narrative
    expect(
      await screen.findByText('This component was separated from OrderService to isolate payment provider logic.'),
    ).toBeVisible()
    // And it cites "Based on 2 recorded events and 1 Pull Request"
    expect(screen.getByText('Based on 2 recorded events and 1 Pull Request')).toBeVisible()
  })

  it('states that not enough information is available when no narrative exists', async () => {
    // Given "PaymentProcessor" has no AI-generated narrative
    mockContextRewind(BASE)
    renderContextRewindPage()
    const user = userEvent.setup()

    // When the developer asks "Why was this changed?"
    await user.click(await screen.findByRole('button', { name: 'Why was this changed?' }))

    // Then the answer states that not enough information is available to answer that
    expect(await screen.findByText('Not enough information is available to answer that.')).toBeVisible()
  })

  it("points to the entity's Pull Request review for \"What did reviewers question?\"", async () => {
    // Given "PaymentProcessor" is referenced by Pull Request 217
    mockContextRewind({
      ...BASE,
      pullRequestReferences: [{ number: 217, repositoryFullName: 'acme/widgets', url: 'https://github.com/acme/widgets/pull/217' }],
    })
    renderContextRewindPage()
    const user = userEvent.setup()

    // When the developer asks "What did reviewers question?"
    await user.click(await screen.findByRole('button', { name: 'What did reviewers question?' }))

    // Then the answer points to the review for Pull Request 217
    expect(await screen.findByText('See the review for Pull Request 217.')).toBeVisible()
  })

  it('states that no Pull Request is recorded for "What did reviewers question?"', async () => {
    // Given "PaymentProcessor" has no Pull Request recorded
    mockContextRewind(BASE)
    renderContextRewindPage()
    const user = userEvent.setup()

    // When the developer asks "What did reviewers question?"
    await user.click(await screen.findByRole('button', { name: 'What did reviewers question?' }))

    // Then the answer states that no Pull Request is recorded
    expect(await screen.findByText('No Pull Request is recorded for this entity.')).toBeVisible()
  })

  it('states plainly that nothing is recorded for "What was decided?"', async () => {
    mockContextRewind(BASE)
    renderContextRewindPage()
    const user = userEvent.setup()

    // When the developer asks "What was decided?"
    await user.click(await screen.findByRole('button', { name: 'What was decided?' }))

    // Then the answer states that nothing is recorded for decisions
    expect(await screen.findByText('Nothing is recorded for decisions.')).toBeVisible()
  })
})

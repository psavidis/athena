import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import ContextRewindPage from './ContextRewindPage'
import type { ContextRewind, PullRequestReview } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/context_rewind_review_comments.feature

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

function mockPullRequestReview(number: number, body: PullRequestReview) {
  server.use(http.get(`/api/repositories/acme/widgets/pulls/${number}/review`, () => HttpResponse.json(body)))
}

const BASE: ContextRewind = {
  entityName: 'PaymentProcessor',
  evolutionTimeline: [],
  pullRequestReferences: [{ number: 217, repositoryFullName: 'acme/widgets', url: 'https://github.com/acme/widgets/pull/217' }],
  aiNarrative: null,
  insufficientHistoryMessage: null,
  knowledgeFacts: [],
}

async function openOverview() {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Zoom in' }))
  return user
}

describe('Context Rewind — Pull Request review comments', () => {
  it('shows review comments grouped by file', async () => {
    // Given Pull Request 217 has a review comment by "octocat" on "PaymentProcessor.java" saying
    // "Can this produce duplicate charges?"
    // And Pull Request 217 has a review comment by "hubot" on "PaymentProcessor.java" saying
    // "Guarded by the idempotency key."
    mockContextRewind(BASE)
    mockPullRequestReview(217, {
      comments: [
        { author: 'octocat', body: 'Can this produce duplicate charges?', path: 'PaymentProcessor.java' },
        { author: 'hubot', body: 'Guarded by the idempotency key.', path: 'PaymentProcessor.java' },
      ],
      reviews: [],
    })
    renderContextRewindPage()
    const user = await openOverview()

    // When the developer opens the review for Pull Request 217
    await user.click(await screen.findByRole('button', { name: 'View review' }))

    // Then the review lists both comments under "PaymentProcessor.java"
    expect(await screen.findByText('PaymentProcessor.java')).toBeVisible()
    expect(screen.getByText('Can this produce duplicate charges?')).toBeVisible()
    expect(screen.getByText('Guarded by the idempotency key.')).toBeVisible()
  })

  it("shows each reviewer's overall verdict", async () => {
    // Given "octocat" approved Pull Request 217
    mockContextRewind(BASE)
    mockPullRequestReview(217, { comments: [], reviews: [{ reviewer: 'octocat', state: 'APPROVED' }] })
    renderContextRewindPage()
    const user = await openOverview()

    // When the developer opens the review for Pull Request 217
    await user.click(await screen.findByRole('button', { name: 'View review' }))

    // Then the review shows "octocat" approved
    expect(await screen.findByText('octocat: APPROVED')).toBeVisible()
  })

  it('states plainly when nothing was recorded for a Pull Request', async () => {
    // Given Pull Request 300 has no review comments or reviews
    mockContextRewind({
      ...BASE,
      pullRequestReferences: [{ number: 300, repositoryFullName: 'acme/widgets', url: 'https://github.com/acme/widgets/pull/300' }],
    })
    mockPullRequestReview(300, { comments: [], reviews: [] })
    renderContextRewindPage()
    const user = await openOverview()

    // When the developer opens the review for Pull Request 300
    await user.click(await screen.findByRole('button', { name: 'View review' }))

    // Then the review states that nothing was recorded for it
    expect(await screen.findByText('Nothing was recorded for this review.')).toBeVisible()
  })
})

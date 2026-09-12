import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import PreSubmissionSummaryPage from './PreSubmissionSummaryPage'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/review_state_and_submission_frontend_rendering.feature

function renderSummaryPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onBack = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <PreSubmissionSummaryPage onBack={onBack} />
    </QueryClientProvider>,
  )
  return { onBack }
}

function mockSummary(body: {
  reviewedChangeTitles: string[]
  mechanicalChangeTitles: string[]
  concernChangeTitles: string[]
  commentCount: number
  gitHubAction: 'APPROVE' | 'REQUEST_CHANGES'
}) {
  server.use(http.get('/api/review/pre-submission-summary', () => HttpResponse.json(body)))
}

describe('Pre-submission summary rendering', () => {
  it('shows reviewed Change count, comment count, and previewed GitHub action', async () => {
    // Given the reviewer is viewing the Change Map
    // When the reviewer opens the pre-submission summary
    mockSummary({
      reviewedChangeTitles: ['Rename greet to salute'],
      mechanicalChangeTitles: [],
      concernChangeTitles: [],
      commentCount: 2,
      gitHubAction: 'APPROVE',
    })

    renderSummaryPage()

    // Then the summary shows the reviewed Change count
    expect(await screen.findByText('Rename greet to salute')).toBeVisible()
    // And the summary shows the comment count
    expect(screen.getByText('Comments: 2')).toBeVisible()
    // And the summary shows the previewed GitHub action
    expect(screen.getByText('Approve')).toBeVisible()
  })

  it('shows "Request changes" when there is an open concern', async () => {
    mockSummary({
      reviewedChangeTitles: [],
      mechanicalChangeTitles: [],
      concernChangeTitles: ['Rename greet to salute'],
      commentCount: 0,
      gitHubAction: 'REQUEST_CHANGES',
    })

    renderSummaryPage()

    expect(await screen.findByText('Request changes')).toBeVisible()
  })

  it('confirming submission shows that it succeeded', async () => {
    // Given the reviewer is viewing the pre-submission summary
    mockSummary({
      reviewedChangeTitles: ['Rename greet to salute'],
      mechanicalChangeTitles: [],
      concernChangeTitles: [],
      commentCount: 1,
      gitHubAction: 'APPROVE',
    })
    server.use(
      http.post('/api/review/submit', () =>
        HttpResponse.json({ fullySynced: true, syncedCommentCount: 1, failedCommentCount: 0 }),
      ),
    )

    renderSummaryPage()
    const user = userEvent.setup()

    // When the reviewer confirms submission
    await user.click(await screen.findByRole('button', { name: 'Confirm and submit' }))

    // Then the reviewer sees that the submission succeeded
    expect(await screen.findByText('Your review was submitted to GitHub.')).toBeVisible()
  })

  it('navigating back without confirming returns to the Change Map', async () => {
    mockSummary({
      reviewedChangeTitles: [],
      mechanicalChangeTitles: [],
      concernChangeTitles: [],
      commentCount: 0,
      gitHubAction: 'APPROVE',
    })

    const { onBack } = renderSummaryPage()
    const user = userEvent.setup()

    await user.click(await screen.findByText('← Back to Change Map'))

    expect(onBack).toHaveBeenCalled()
  })
})

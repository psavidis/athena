import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import AiAnalysisPage from './AiAnalysisPage'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ai_analysis_frontend_rendering.feature

function renderAiAnalysisPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onBack = vi.fn()
  const onSelectChange = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <AiAnalysisPage onBack={onBack} onSelectChange={onSelectChange} />
    </QueryClientProvider>,
  )
  return { onBack, onSelectChange }
}

function mockBoundary(body: {
  includedChangeTitles: string[]
  excludedUnreviewedChangeTitles: string[]
  excludedGeneratedChangeTitles: string[]
  privateNotesExcluded: boolean
}) {
  server.use(http.get('/api/review/ai-context-boundary', () => HttpResponse.json(body)))
}

const DEFAULT_BOUNDARY = {
  includedChangeTitles: ['Rename greet to salute'],
  excludedUnreviewedChangeTitles: [],
  excludedGeneratedChangeTitles: [],
  privateNotesExcluded: true,
}

describe('AI analysis rendering', () => {
  it('shows which Changes are included and excluded before triggering analysis', async () => {
    // Given the reviewer is viewing the Change Map
    // When the reviewer opens the AI analysis view
    mockBoundary({
      includedChangeTitles: ['Rename greet to salute'],
      excludedUnreviewedChangeTitles: ['Move authenticate to Account'],
      excludedGeneratedChangeTitles: [],
      privateNotesExcluded: true,
    })

    renderAiAnalysisPage()

    // Then the view shows which Changes are included
    expect(await screen.findByText('Rename greet to salute')).toBeVisible()
    // And the view shows which Changes are excluded and why
    expect(screen.getByText('Move authenticate to Account')).toBeVisible()
    expect(screen.getByText('Excluded — not yet reviewed')).toBeVisible()
  })

  it('triggers AI analysis and shows each finding as pending', async () => {
    mockBoundary(DEFAULT_BOUNDARY)
    server.use(
      http.post('/api/review/ai-analysis', () =>
        HttpResponse.json([
          { id: '1', description: 'You may have missed the null check', disposition: 'PENDING', jumpTargetChangeKey: null },
        ]),
      ),
    )

    renderAiAnalysisPage()
    const user = userEvent.setup()

    // When the reviewer triggers AI analysis
    await user.click(await screen.findByRole('button', { name: 'Trigger AI analysis' }))

    // Then the findings list shows each finding as pending
    expect(await screen.findByText('You may have missed the null check')).toBeVisible()
    expect(screen.getByText('Pending')).toBeVisible()
  })

  it('accepts a finding', async () => {
    mockBoundary(DEFAULT_BOUNDARY)
    server.use(
      http.post('/api/review/ai-analysis', () =>
        HttpResponse.json([
          { id: '1', description: 'You may have missed the null check', disposition: 'PENDING', jumpTargetChangeKey: null },
        ]),
      ),
      http.post('/api/review/ai-findings/1/accept', () =>
        HttpResponse.json([
          { id: '1', description: 'You may have missed the null check', disposition: 'ACCEPTED', jumpTargetChangeKey: null },
        ]),
      ),
    )

    renderAiAnalysisPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Trigger AI analysis' }))
    await screen.findByText('You may have missed the null check')

    // When the reviewer accepts that finding
    await user.click(screen.getByRole('button', { name: 'Accept' }))

    // Then that finding is shown as accepted
    expect(await screen.findByText('Accepted')).toBeVisible()
  })

  it('dismisses a finding', async () => {
    mockBoundary(DEFAULT_BOUNDARY)
    server.use(
      http.post('/api/review/ai-analysis', () =>
        HttpResponse.json([
          { id: '1', description: 'You may have missed the null check', disposition: 'PENDING', jumpTargetChangeKey: null },
        ]),
      ),
      http.post('/api/review/ai-findings/1/dismiss', () =>
        HttpResponse.json([
          { id: '1', description: 'You may have missed the null check', disposition: 'DISMISSED', jumpTargetChangeKey: null },
        ]),
      ),
    )

    renderAiAnalysisPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Trigger AI analysis' }))
    await screen.findByText('You may have missed the null check')

    // When the reviewer dismisses that finding
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))

    // Then that finding is shown as dismissed
    expect(await screen.findByText('Dismissed')).toBeVisible()
  })

  it('jumps from a finding to its related Change', async () => {
    mockBoundary(DEFAULT_BOUNDARY)
    server.use(
      http.post('/api/review/ai-analysis', () =>
        HttpResponse.json([
          {
            id: '1',
            description: 'Consider the rename\'s effect on callers',
            disposition: 'PENDING',
            jumpTargetChangeKey: 'the-rename-change-key',
          },
        ]),
      ),
    )

    const { onSelectChange } = renderAiAnalysisPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Trigger AI analysis' }))
    await screen.findByText("Consider the rename's effect on callers")

    // When the reviewer clicks the finding's jump target
    await user.click(screen.getByRole('button', { name: 'Jump to Change →' }))

    // Then the reviewer sees that Change's detail view
    expect(onSelectChange).toHaveBeenCalledWith('the-rename-change-key')
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import AiAnalysisPage from './AiAnalysisPage'
import { server } from './test/server'

// Traces knowledge_candidate_capture.feature (ticket #118): saving an
// accepted finding to the Knowledge Base from the AI analysis view.

function renderAiAnalysisPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AiAnalysisPage onBack={vi.fn()} onSelectChange={vi.fn()} />
    </QueryClientProvider>,
  )
}

const BOUNDARY = {
  includedChangeTitles: ['Rename greet to salute'],
  excludedUnreviewedChangeTitles: [],
  excludedGeneratedChangeTitles: [],
  privateNotesExcluded: true,
}

async function triggerAnalysisAndAccept(user: ReturnType<typeof userEvent.setup>) {
  server.use(
    http.get('/api/review/ai-context-boundary', () => HttpResponse.json(BOUNDARY)),
    http.post('/api/review/ai-analysis', () =>
      HttpResponse.json([
        {
          id: '1',
          description: 'Retry configuration intentionally overridden per environment',
          disposition: 'PENDING',
          jumpTargetChangeKey: null,
        },
      ]),
    ),
    http.post('/api/review/ai-findings/1/accept', () =>
      HttpResponse.json([
        {
          id: '1',
          description: 'Retry configuration intentionally overridden per environment',
          disposition: 'ACCEPTED',
          jumpTargetChangeKey: null,
        },
      ]),
    ),
  )

  renderAiAnalysisPage()
  await user.click(await screen.findByRole('button', { name: 'Trigger AI analysis' }))
  await screen.findByText('Retry configuration intentionally overridden per environment')
  await user.click(screen.getByRole('button', { name: 'Accept' }))
  await screen.findByText('Accepted')
}

describe('Knowledge candidate capture from AI analysis', () => {
  it('saves an accepted finding to the Knowledge Base', async () => {
    const user = userEvent.setup()
    await triggerAnalysisAndAccept(user)
    server.use(
      http.post('/api/review/ai-findings/1/save-to-knowledge-base', () =>
        HttpResponse.json({ success: true, providerId: 'obsidian', storedAs: 'Athena Knowledge/note.md' }),
      ),
    )

    // When the user saves that finding to the Knowledge Base
    await user.click(screen.getByRole('button', { name: 'Save to Knowledge Base' }))

    // Then the page confirms it was saved
    expect(await screen.findByText('Saved to Knowledge Base.')).toBeVisible()
  })

  it('shows an error when no Knowledge Provider is configured', async () => {
    const user = userEvent.setup()
    await triggerAnalysisAndAccept(user)
    server.use(
      http.post('/api/review/ai-findings/1/save-to-knowledge-base', () => new HttpResponse(null, { status: 503 })),
    )

    await user.click(screen.getByRole('button', { name: 'Save to Knowledge Base' }))

    expect(await screen.findByText('Could not save — is a Knowledge Provider configured?')).toBeVisible()
  })
})

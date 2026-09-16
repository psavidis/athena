import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import AiAnalysisPage from './AiAnalysisPage'
import { server } from './test/server'
import { pressShortcut } from './test/keyboardShortcuts'

// Traces the AI-finding-navigation scenarios of
// frontend/src/test/resources/features/ui_first_experience/keyboard_review_navigation.feature

function renderAiAnalysisPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onSelectChange = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <AiAnalysisPage onBack={vi.fn()} onSelectChange={onSelectChange} />
    </QueryClientProvider>,
  )
  return { onSelectChange }
}

const DEFAULT_BOUNDARY = {
  includedChangeTitles: ['Rename greet to salute'],
  excludedUnreviewedChangeTitles: [],
  excludedGeneratedChangeTitles: [],
  privateNotesExcluded: true,
}

function mockBoundary() {
  server.use(http.get('/api/review/ai-context-boundary', () => HttpResponse.json(DEFAULT_BOUNDARY)))
}

async function triggerAnalysisWith(findings: unknown[]) {
  mockBoundary()
  server.use(http.post('/api/review/ai-analysis', () => HttpResponse.json(findings)))
  const rendered = renderAiAnalysisPage()
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Trigger AI analysis' }))
  return { user, ...rendered }
}

describe('Keyboard navigation of AI findings', () => {
  it('moves keyboard focus to the next AI finding', async () => {
    await triggerAnalysisWith([
      { id: '1', description: 'First finding', disposition: 'PENDING', jumpTargetChangeKey: null },
      { id: '2', description: 'Second finding', disposition: 'PENDING', jumpTargetChangeKey: null },
    ])
    const firstFinding = (await screen.findByText('First finding')).closest('[data-testid="finding-item"]') as HTMLElement
    firstFinding.focus()

    pressShortcut('nextFinding', firstFinding)

    const secondFinding = screen.getByText('Second finding').closest('[data-testid="finding-item"]')
    expect(document.activeElement).toBe(secondFinding)
  })

  it('jumps from a finding to its related Change with the keyboard', async () => {
    const { onSelectChange } = await triggerAnalysisWith([
      { id: '1', description: "Consider the rename's effect on callers", disposition: 'PENDING', jumpTargetChangeKey: 'the-rename-change-key' },
    ])

    const finding = (await screen.findByText("Consider the rename's effect on callers")).closest(
      '[data-testid="finding-item"]',
    ) as HTMLElement
    finding.focus()

    pressShortcut('jumpToRelatedChange', finding)

    expect(onSelectChange).toHaveBeenCalledWith('the-rename-change-key')
  })

  it('a finding with no related Change has no jump target, and focus does not move', async () => {
    await triggerAnalysisWith([{ id: '1', description: 'No related Change', disposition: 'PENDING', jumpTargetChangeKey: null }])
    const finding = (await screen.findByText('No related Change')).closest('[data-testid="finding-item"]') as HTMLElement
    finding.focus()

    pressShortcut('jumpToRelatedChange', finding)

    expect(document.activeElement).toBe(finding)
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import ContextRewindPage from './ContextRewindPage'
import type { ContextRewind, ModuleTopology } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/context_rewind_context_map.feature

function renderContextRewindPage(moduleName?: string, onOpenModule = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ContextRewindPage entityName="PaymentProcessor" onBack={vi.fn()} moduleName={moduleName} onOpenModule={onOpenModule} />
    </QueryClientProvider>,
  )
  return { onOpenModule }
}

function mockContextRewind(body: ContextRewind) {
  server.use(http.get('/api/review/context-rewind/PaymentProcessor', () => HttpResponse.json(body)))
}

function mockTopology(topology: ModuleTopology) {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(topology)))
}

const BASE: ContextRewind = {
  entityName: 'PaymentProcessor',
  evolutionTimeline: [],
  pullRequestReferences: [],
  aiNarrative: null,
  insufficientHistoryMessage: null,
  knowledgeFacts: [],
}

const TOPOLOGY: ModuleTopology = {
  territories: [
    { moduleName: 'crowdness-live', status: 'TOUCHED', fileCount: 5, statusSummary: '5 files', techStack: 'SPRING_BOOT_JAVA', techStackLabel: 'Spring Boot · Java', changeKeys: [] },
    { moduleName: 'crowdness-ingestion', status: 'TOUCHED', fileCount: 3, statusSummary: '3 files', techStack: 'JAVA', techStackLabel: 'Java', changeKeys: [] },
    { moduleName: 'crowdness-payments-api', status: 'TOUCHED', fileCount: 2, statusSummary: '2 files', techStack: 'JAVA', techStackLabel: 'Java', changeKeys: [] },
  ],
  dependencies: [
    { from: 'crowdness-live', to: 'crowdness-ingestion' },
    { from: 'crowdness-payments-api', to: 'crowdness-live' },
  ],
}

describe('Context Rewind — Context Map', () => {
  it("shows the entity's module and its direct relationships", async () => {
    // Given "PaymentProcessor" lives in the "crowdness-live" module
    // And "crowdness-live" depends on "crowdness-ingestion"
    // And "crowdness-payments-api" depends on "crowdness-live"
    mockContextRewind(BASE)
    mockTopology(TOPOLOGY)
    renderContextRewindPage('crowdness-live')
    const user = userEvent.setup()

    // When the developer opens the Context Map for "PaymentProcessor"
    await user.click(await screen.findByRole('button', { name: 'Context Map' }))

    // Then the map shows "crowdness-live" depends on "crowdness-ingestion"
    expect(await screen.findByText('crowdness-live depends on crowdness-ingestion')).toBeVisible()
    // And it shows "crowdness-payments-api" depends on "crowdness-live"
    expect(screen.getByText('crowdness-payments-api depends on crowdness-live')).toBeVisible()
  })

  it('returns to the canvas focused on a selected related module', async () => {
    // Given the developer has the Context Map open for "PaymentProcessor" in the "crowdness-live" module
    mockContextRewind(BASE)
    mockTopology(TOPOLOGY)
    const { onOpenModule } = renderContextRewindPage('crowdness-live')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Context Map' }))
    await screen.findByText('crowdness-live depends on crowdness-ingestion')

    // When the developer selects the related module "crowdness-ingestion"
    await user.click(screen.getByRole('button', { name: 'crowdness-ingestion' }))

    // Then the developer returns to the Semantic Canvas focused on "crowdness-ingestion"
    expect(onOpenModule).toHaveBeenCalledWith('crowdness-ingestion')
  })

  it("isn't offered when the entity's module isn't known", async () => {
    // Given the developer opens Context Rewind for "PaymentProcessor" with no known module
    mockContextRewind(BASE)
    renderContextRewindPage(undefined)

    // Then no Context Map action is offered
    await screen.findByRole('button', { name: 'Zoom in' })
    expect(screen.queryByRole('button', { name: 'Context Map' })).not.toBeInTheDocument()
  })
})

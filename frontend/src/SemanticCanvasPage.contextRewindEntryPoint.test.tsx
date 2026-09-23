import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ContextRewind, ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/context_rewind_story_timeline.feature
// (the "invoking Context Rewind on an entity" entry point, mirroring how the drawer's own
// Comment affordance is invoked today) and context_rewind_pr_integration.feature (ticket #194 —
// Context Rewind renders as an overlay over the still-mounted canvas, not a page-level swap).

function mockTopology(topology: ModuleTopology) {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(topology)))
}

function mockModuleProfile(moduleName: string, profile: SemanticProfile) {
  server.use(http.get(`/api/review/modules/${moduleName}/semantic-profile`, () => HttpResponse.json(profile)))
}

function mockContextRewind(entityName: string, body: ContextRewind) {
  server.use(http.get(`/api/review/context-rewind/${entityName}`, () => HttpResponse.json(body)))
}

function renderCanvas() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticCanvasPage pullRequest={null} onNotConnected={vi.fn()} onNoPullRequestSelected={vi.fn()} />
    </QueryClientProvider>,
  )
}

const TOPOLOGY: ModuleTopology = {
  territories: [
    {
      moduleName: 'crowdness-live',
      status: 'TOUCHED',
      fileCount: 5,
      statusSummary: '5 files',
      techStack: 'SPRING_BOOT_JAVA',
      techStackLabel: 'Spring Boot · Java',
      changeKeys: ['change-key-1'],
      testChangeKeys: [],
    },
    {
      moduleName: 'crowdness-ingestion',
      status: 'TOUCHED',
      fileCount: 3,
      statusSummary: '3 files',
      techStack: 'JAVA',
      techStackLabel: 'Java',
      changeKeys: [],
      testChangeKeys: [],
    },
  ],
  dependencies: [],
}

const PROFILE: SemanticProfile = {
  dimensions: [
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Idempotent recovery',
      conceptDescription: 'Description',
      inferred: false,
      confidencePercent: 100,
      evidence: [],
      supportingConceptNames: [],
      filesTouched: ['PaymentValidator.java'],
    },
  ],
}

async function openFileDrawer() {
  mockTopology(TOPOLOGY)
  mockModuleProfile('crowdness-live', PROFILE)
  server.use(
    http.get('/api/review/changes/change-key-1', () =>
      HttpResponse.json({
        changeKey: 'change-key-1',
        category: 'BEHAVIORAL',
        kind: 'ADD_SYMBOL',
        description: 'Add recovery check',
        symbols: [],
        files: ['PaymentValidator.java'],
        diff: '',
      }),
    ),
  )
  renderCanvas()
  const user = userEvent.setup()
  const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
  await user.click(territory)
  await user.click(await screen.findByText('PaymentValidator.java'))
  return { user }
}

describe('Context Rewind entry point on the detail drawer', () => {
  it('offers a Rewind action on a file selection', async () => {
    await openFileDrawer()

    expect(await screen.findByRole('button', { name: /Rewind/ })).toBeVisible()
  })

  it('opens Context Rewind for that file as an overlay over the still-mounted canvas', async () => {
    mockContextRewind('PaymentValidator', {
      entityName: 'PaymentValidator',
      evolutionTimeline: [],
      pullRequestReferences: [],
      aiNarrative: null,
      insufficientHistoryMessage: null,
      knowledgeFacts: [],
    })
    const { user } = await openFileDrawer()

    await user.click(await screen.findByRole('button', { name: /Rewind/ }))

    // Context Rewind is visible...
    expect(await screen.findByText('Context Rewind')).toBeVisible()
    // ...and the canvas underneath is still mounted (not replaced), evidenced by its territory
    // button still being present in the DOM.
    expect(screen.getByRole('button', { name: 'crowdness-live territory' })).toBeInTheDocument()
  })
})

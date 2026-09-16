import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ContextRewind, ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/context_rewind_pr_integration.feature

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

function fireEventPan(canvas: HTMLElement) {
  fireEvent.pointerDown(canvas, { clientX: 0, clientY: 0 })
  fireEvent.pointerMove(canvas, { clientX: 40, clientY: 20 })
  fireEvent.pointerUp(canvas)
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

const EMPTY_CONTEXT_REWIND: ContextRewind = {
  entityName: 'PaymentValidator',
  evolutionTimeline: [],
  pullRequestReferences: [],
  aiNarrative: null,
  insufficientHistoryMessage: null,
  knowledgeFacts: [],
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
  mockContextRewind('PaymentValidator', EMPTY_CONTEXT_REWIND)
  renderCanvas()
  const user = userEvent.setup()
  const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
  await user.click(territory)
  await user.click(await screen.findByText('PaymentValidator.java'))
  return { user }
}

describe('Context Rewind — PR review integration', () => {
  it('does not move or resize the canvas underneath it when Rewind is invoked', async () => {
    // Given the reviewer has panned the canvas to a specific position while reviewing a PR
    const { user } = await openFileDrawer()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    fireEventPan(canvas)
    const transformBeforeRewind = surface.style.transform

    // When the reviewer invokes Rewind on a changed file
    await user.click(await screen.findByRole('button', { name: /Rewind/ }))

    // Then the canvas's pan position is unchanged underneath
    expect(await screen.findByText('Context Rewind')).toBeVisible()
    expect(surface.style.transform).toBe(transformBeforeRewind)
  })

  it('returns to exactly where the reviewer left the PR with a single action', async () => {
    // Given the reviewer has invoked Rewind on a changed file, panning the canvas beforehand
    const { user } = await openFileDrawer()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    fireEventPan(canvas)
    const transformBeforeRewind = surface.style.transform
    await user.click(await screen.findByRole('button', { name: /Rewind/ }))
    await screen.findByText('Context Rewind')

    // When the reviewer selects "← Back" in Context Rewind
    await user.click(screen.getByRole('button', { name: '← Back' }))

    // Then Context Rewind is no longer visible
    expect(screen.queryByText('Context Rewind')).not.toBeInTheDocument()
    // And the canvas's pan position is still unchanged
    expect(surface.style.transform).toBe(transformBeforeRewind)
  })
})

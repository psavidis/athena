import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'

// A PR with many Changes each independently classified with the same
// Capability-level concept (e.g. many "Add Capability" classifications) used
// to render one identical card per Change — the backend now folds them into
// one card with a grouped-count badge, matching the treatment
// capability-extraction already got. See RepeatedClassificationGrouper.

function mockTopology(topology: ModuleTopology) {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(topology)))
}

function mockModuleProfile(moduleName: string, profile: SemanticProfile) {
  server.use(http.get(`/api/review/modules/${moduleName}/semantic-profile`, () => HttpResponse.json(profile)))
}

function renderCanvas() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticCanvasPage pullRequest={null} onNotConnected={vi.fn()} onNoPullRequestSelected={vi.fn()} />
    </QueryClientProvider>,
  )
}

const ONE_TERRITORY: ModuleTopology = {
  territories: [
    {
      moduleName: 'crowdness-live',
      status: 'NEW',
      fileCount: 34,
      statusSummary: '34 files · new module',
      techStack: 'SPRING_BOOT_JAVA',
      techStackLabel: 'Spring Boot · Java',
      changeKeys: [],
    },
  ],
  dependencies: [],
}

describe('Capability level — repeated classifications', () => {
  it('folds many identical Capability classifications into one card with a count badge', async () => {
    mockTopology(ONE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        {
          dimension: 'RESPONSIBILITY',
          conceptName: 'Add Capability',
          conceptDescription: 'A new business-meaningful capability was introduced.',
          inferred: true,
          confidencePercent: 70,
          evidence: [],
          supportingConceptNames: [],
          filesTouched: [],
          groupedMoveCount: 17,
        },
      ],
    })
    renderCanvas()

    await userEvent.setup().click(await screen.findByRole('button', { name: 'crowdness-live territory' }))

    const nodes = await screen.findAllByTestId('concept-node')
    expect(nodes).toHaveLength(1)
    expect(screen.getByText('Add Capability')).toBeVisible()
    expect(screen.getByTestId('grouped-count-badge')).toHaveTextContent('17×')
  })

  it('shows no badge for a Capability classification that only occurred once', async () => {
    mockTopology(ONE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        {
          dimension: 'RESPONSIBILITY',
          conceptName: 'Add Capability',
          conceptDescription: 'A new business-meaningful capability was introduced.',
          inferred: true,
          confidencePercent: 70,
          evidence: [],
          supportingConceptNames: [],
          filesTouched: [],
        },
      ],
    })
    renderCanvas()

    await userEvent.setup().click(await screen.findByRole('button', { name: 'crowdness-live territory' }))

    await screen.findAllByTestId('concept-node')
    expect(screen.queryByTestId('grouped-count-badge')).not.toBeInTheDocument()
  })
})

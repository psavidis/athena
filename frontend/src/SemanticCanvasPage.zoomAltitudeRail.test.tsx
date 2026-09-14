import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology, SemanticDimensionEntry, SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_canvas_zoom_altitude_rail.feature
// (REST-endpoint chip and client-adapter marker scenarios are NOT covered here — that
// classifier-level detection doesn't exist yet, flagged back to the ticket.)

function mockTopology(topology: ModuleTopology) {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(topology)))
}

function mockModuleProfile(moduleName: string, profile: SemanticProfile) {
  server.use(
    http.get(`/api/review/modules/${moduleName}/semantic-profile`, () => HttpResponse.json(profile)),
  )
}

function renderCanvas() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticCanvasPage onNotConnected={vi.fn()} onNoPullRequestSelected={vi.fn()} />
    </QueryClientProvider>,
  )
}

const LIVE_TERRITORY: ModuleTopology = {
  territories: [
    {
      moduleName: 'crowdness-live',
      status: 'TOUCHED',
      fileCount: 5,
      statusSummary: '5 files',
      techStack: 'SPRING_BOOT_JAVA',
      techStackLabel: 'Spring Boot · Java',
      changeKeys: [],
    },
  ],
  dependencies: [],
}

function entry(overrides: Partial<SemanticDimensionEntry>): SemanticDimensionEntry {
  return {
    dimension: 'STRUCTURAL',
    conceptName: 'Concept',
    conceptDescription: 'Description',
    inferred: false,
    confidencePercent: 100,
    evidence: [],
    supportingConceptNames: [],
    ...overrides,
  }
}

async function diveIntoLive() {
  const user = userEvent.setup()
  const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
  await user.click(territory)
  return user
}

describe('Semantic Canvas — zoom-altitude rail', () => {
  it('compresses the rail to only the dimensions this PR has content for', async () => {
    // Given a PR classified only at Intent and Structure
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        entry({ dimension: 'INTENT', conceptName: 'Enforce payment expiration' }),
        entry({ dimension: 'STRUCTURAL', conceptName: 'Add validation', filesTouched: ['Order.java'] }),
      ],
    })
    renderCanvas()

    // Then the zoom-altitude rail shows only the Intent and Structure stops
    await diveIntoLive()
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    expect(within(rail).getByRole('button', { name: /Intent/ })).toBeVisible()
    expect(within(rail).getByRole('button', { name: /Structure/ })).toBeVisible()
    expect(within(rail).queryByRole('button', { name: /Architecture/ })).not.toBeInTheDocument()
    expect(within(rail).queryByRole('button', { name: /Pattern\+Framework/ })).not.toBeInTheDocument()
  })

  it("moves the camera to the PR's headline node when Intent is selected", async () => {
    // Given the Intent stop is classified as "Enforce payment expiration"
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [entry({ dimension: 'INTENT', conceptName: 'Enforce payment expiration' })],
    })
    renderCanvas()
    await diveIntoLive()

    // When the reviewer selects the Intent stop
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await userEvent.setup().click(within(rail).getByRole('button', { name: /Intent/ }))

    // Then the camera shows the headline node
    expect(await screen.findByText('Enforce payment expiration')).toBeVisible()
  })

  it('reveals internal layer shape at the Architecture stop', async () => {
    // Given the territory has Architecture-level content
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        entry({ dimension: 'ARCHITECTURE', conceptName: 'Domain Layer' }),
        entry({ dimension: 'ARCHITECTURE', conceptName: 'Application Layer' }),
      ],
    })
    renderCanvas()
    await diveIntoLive()

    // When the reviewer selects the Architecture stop
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await userEvent.setup().click(within(rail).getByRole('button', { name: /Architecture/ }))

    // Then the layer shape is revealed
    expect(await screen.findByTestId('architecture-layer-shape')).toBeVisible()
    expect(screen.getByText('Domain Layer')).toBeVisible()
    expect(screen.getByText('Application Layer')).toBeVisible()
  })

  it('shows one node per production file, aggregating test files into a Test suite node', async () => {
    // Given the territory touches 2 production files and 3 test files
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        entry({
          dimension: 'STRUCTURAL',
          conceptName: 'Add validation',
          filesTouched: [
            'OrderService.java',
            'PaymentValidator.java',
            'OrderServiceTest.java',
            'PaymentValidatorTest.java',
            'IntegrationSmokeTest.java',
          ],
        }),
      ],
    })
    renderCanvas()
    await diveIntoLive()

    // When the reviewer selects the Structure stop
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await userEvent.setup().click(within(rail).getByRole('button', { name: /Structure/ }))

    // Then one node per production file, and a single Test suite node instead of 3
    expect(await screen.findByText('OrderService.java')).toBeVisible()
    expect(screen.getByText('PaymentValidator.java')).toBeVisible()
    expect(screen.queryByText('OrderServiceTest.java')).not.toBeInTheDocument()
    expect(screen.getByTestId('test-suite-node')).toHaveTextContent('Test suite (3 files)')
  })

  it('expands the Test suite node to list the actual test files', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        entry({
          dimension: 'STRUCTURAL',
          conceptName: 'Add validation',
          filesTouched: ['OrderServiceTest.java'],
        }),
      ],
    })
    renderCanvas()
    await diveIntoLive()
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await userEvent.setup().click(within(rail).getByRole('button', { name: /Structure/ }))

    // When the reviewer expands the Test suite node
    const testSuiteNode = await screen.findByTestId('test-suite-node')
    await userEvent.setup().click(within(testSuiteNode).getByText(/Test suite/))

    // Then the actual list of test files is shown
    expect(within(testSuiteNode).getByText('OrderServiceTest.java')).toBeVisible()
  })

  it('shows a distinct icon for a config/build file vs. a production file', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        entry({
          dimension: 'STRUCTURAL',
          conceptName: 'Wire config',
          filesTouched: ['crowdness-live/application.yml', 'OrderService.java'],
        }),
      ],
    })
    renderCanvas()
    await diveIntoLive()
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await userEvent.setup().click(within(rail).getByRole('button', { name: /Structure/ }))

    const configNode = (await screen.findByText('crowdness-live/application.yml')).closest('button')!
    const prodNode = screen.getByText('OrderService.java').closest('button')!
    expect(within(configNode).getByTestId('config-icon')).toBeVisible()
    expect(within(prodNode).getByTestId('file-icon')).toBeVisible()
  })

  it('lands on the first populated altitude stop when diving into a territory', async () => {
    // Given no Architecture content but Pattern content exists
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [entry({ dimension: 'PATTERN', conceptName: 'Idempotent recovery' })],
    })
    renderCanvas()

    // When the reviewer dives into the territory
    await diveIntoLive()

    // Then the camera lands on Pattern+Framework, not a fixed default
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    expect(within(rail).getByRole('button', { name: /Pattern\+Framework/ })).toHaveAttribute('aria-current', 'true')
    expect(await screen.findByText('Idempotent recovery')).toBeVisible()
  })

  it('zooms the camera in further for a file node than a concept card', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [entry({ dimension: 'STRUCTURAL', conceptName: 'Add validation', filesTouched: ['Order.java'] })],
    })
    renderCanvas()
    await diveIntoLive()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement

    // When the reviewer selects a file node
    await userEvent.setup().click(await screen.findByText('Order.java'))

    // Then the camera zooms in to the maximum scale (closer than a concept card's target)
    expect(surface.style.transform).toContain('scale(2.5)')
  })

  it('never exceeds the maximum zoom however many nodes are selected in a row', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        entry({
          dimension: 'STRUCTURAL',
          conceptName: 'Add validation',
          filesTouched: ['Order.java', 'Payment.java', 'Validator.java'],
        }),
      ],
    })
    renderCanvas()
    await diveIntoLive()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    const user = userEvent.setup()

    await user.click(await screen.findByText('Order.java'))
    await user.click(screen.getByText('Payment.java'))
    await user.click(screen.getByText('Validator.java'))

    expect(surface.style.transform).toContain('scale(2.5)')
    expect(surface.style.transform).not.toMatch(/scale\(([3-9]|\d{2,})/)
  })

  it('overlays a dimension badge on every node when the Semantic layers toggle is on', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [entry({ dimension: 'PATTERN', conceptName: 'Idempotent recovery' })],
    })
    renderCanvas()
    await diveIntoLive()

    // When the reviewer turns on the Semantic layers toggle
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Semantic layers' }))

    // Then the node shows its dimension badge
    expect(await screen.findByTestId('dimension-badge')).toHaveTextContent('PATTERN')
  })

  it('removes the dimension badges when the toggle is turned back off', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', {
      dimensions: [entry({ dimension: 'PATTERN', conceptName: 'Idempotent recovery' })],
    })
    renderCanvas()
    await diveIntoLive()
    const user = userEvent.setup()
    const toggle = await screen.findByRole('button', { name: 'Semantic layers' })
    await user.click(toggle)
    expect(await screen.findByTestId('dimension-badge')).toBeVisible()

    // When the reviewer turns the toggle off
    await user.click(toggle)

    // Then no node shows a dimension badge
    expect(screen.queryByTestId('dimension-badge')).not.toBeInTheDocument()
  })
})

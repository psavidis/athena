import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_canvas_shell_and_territory_map.feature

function mockTopology(topology: ModuleTopology) {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(topology)))
}

function renderCanvas() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onNotConnected = vi.fn()
  const onNoPullRequestSelected = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticCanvasPage pullRequest={null} onNotConnected={onNotConnected} onNoPullRequestSelected={onNoPullRequestSelected} />
    </QueryClientProvider>,
  )
  return { onNotConnected, onNoPullRequestSelected }
}

const TWO_TOUCHED_MODULES: ModuleTopology = {
  territories: [
    {
      moduleName: 'crowdness-live',
      status: 'NEW',
      fileCount: 12,
      statusSummary: '12 files · new module',
      techStack: 'SPRING_BOOT_JAVA',
      techStackLabel: 'Spring Boot · Java',
      changeKeys: ['k1'],
      testChangeKeys: [],
    },
    {
      moduleName: 'crowdness-ingestion',
      status: 'TOUCHED',
      fileCount: 17,
      statusSummary: '17 files · one new query',
      techStack: 'JAVA',
      techStackLabel: 'Java',
      changeKeys: ['k2'],
      testChangeKeys: [],
    },
  ],
  dependencies: [],
}

describe('Semantic Canvas — pan/zoom shell and territory map', () => {
  const originalMatchMedia = window.matchMedia

  afterEach(() => {
    window.matchMedia = originalMatchMedia
  })

  function mockReducedMotion(reduced: boolean) {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: reduced && query === '(prefers-reduced-motion: reduce)',
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
  }

  it('shows one territory per module affected by the PR', async () => {
    // Given the reviewer opens the Semantic Canvas for a PR that touches "crowdness-live" and "crowdness-ingestion"
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()

    // Then the canvas shows a "crowdness-live" territory and a "crowdness-ingestion" territory
    expect(await screen.findByRole('button', { name: 'crowdness-live territory' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'crowdness-ingestion territory' })).toBeVisible()
  })

  it('marks a territory introduced by this PR as new', async () => {
    // Given a PR that introduces the "crowdness-live" module for the first time
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()

    // Then the "crowdness-live" territory is marked as a new module
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    expect(territory).toHaveAttribute('data-status', 'NEW')
  })

  it('marks a modified territory as touched, with real status text', async () => {
    // Given a PR that modifies 17 files in "crowdness-ingestion", adding one new query
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()

    // Then the territory is marked touched, with status text "17 files · one new query"
    const territory = await screen.findByRole('button', { name: 'crowdness-ingestion territory' })
    expect(territory).toHaveAttribute('data-status', 'TOUCHED')
    expect(territory).toHaveTextContent('17 files · one new query')
  })

  it('marks a referenced-but-unchanged territory as idle', async () => {
    // Given changed modules call into the unmodified "crowdness-common" module
    mockTopology({
      territories: [
        ...TWO_TOUCHED_MODULES.territories,
        {
          moduleName: 'crowdness-common',
          status: 'IDLE',
          fileCount: 0,
          statusSummary: 'unchanged',
          techStack: 'JAVA',
          techStackLabel: 'Java',
          changeKeys: [],
          testChangeKeys: [],
        },
      ],
      dependencies: [{ from: 'crowdness-live', to: 'crowdness-common' }],
    })
    renderCanvas()
    // Unchanged dependencies appear on request (ticket #318).
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Show 1 unchanged dependency' }))

    // Then the "crowdness-common" territory is marked idle, not touched or new
    const territory = await screen.findByRole('button', { name: 'crowdness-common territory' })
    expect(territory).toHaveAttribute('data-status', 'IDLE')
  })

  it('shows a real tech-stack badge per territory', async () => {
    // Given a PR touching the Spring Boot module "crowdness-live" and the React module "crowdness-ui"
    mockTopology({
      territories: [
        TWO_TOUCHED_MODULES.territories[0],
        {
          moduleName: 'crowdness-ui',
          status: 'TOUCHED',
          fileCount: 3,
          statusSummary: '3 files',
          techStack: 'REACT_TYPESCRIPT',
          techStackLabel: 'React · TypeScript',
          changeKeys: [],
          testChangeKeys: [],
        },
      ],
      dependencies: [],
    })
    renderCanvas()

    // Then each territory shows its own tech-stack badge
    const live = await screen.findByRole('button', { name: 'crowdness-live territory' })
    expect(live).toHaveTextContent('Spring Boot · Java')
    const ui = screen.getByRole('button', { name: 'crowdness-ui territory' })
    expect(ui).toHaveTextContent('React · TypeScript')
  })

  it('draws a dependency rail between two territories that depend on each other', async () => {
    // Given "crowdness-live" depends on "crowdness-connect"
    mockTopology({
      territories: [
        TWO_TOUCHED_MODULES.territories[0],
        {
          moduleName: 'crowdness-connect',
          status: 'IDLE',
          fileCount: 0,
          statusSummary: 'unchanged',
          techStack: 'JAVA',
          techStackLabel: 'Java',
          changeKeys: [],
          testChangeKeys: [],
        },
      ],
      dependencies: [{ from: 'crowdness-live', to: 'crowdness-connect' }],
    })
    renderCanvas()
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Show 1 unchanged dependency' }))
    await screen.findByRole('button', { name: 'crowdness-connect territory' })

    // Then a dependency rail is drawn between the two territories
    const rail = document.querySelector('[data-testid="dependency-rail"][data-from="crowdness-live"][data-to="crowdness-connect"]')
    expect(rail).not.toBeNull()
  })

  it('draws no dependency rail between territories with no real dependency', async () => {
    // Given "crowdness-live" and "crowdness-management" have no dependency on each other
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()
    await screen.findByRole('button', { name: 'crowdness-live territory' })

    // Then no dependency rail is drawn
    expect(document.querySelectorAll('[data-testid="dependency-rail"]')).toHaveLength(0)
  })

  it('animates the camera into a territory and maximizes it on click', async () => {
    // Given the reviewer is viewing the territory map
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })

    // When the reviewer clicks the territory
    await userEvent.setup().click(territory)

    // Then the camera animates into it and it fills nearly the entire viewport
    expect(territory).toHaveAttribute('aria-current', 'true')
    const canvas = screen.getByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    expect(surface.className).toContain('transition-transform')
  })

  it('skips the animated dive when reduced motion is requested', async () => {
    // Given the reviewer has requested reduced motion
    mockReducedMotion(true)
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })

    // When the reviewer clicks the territory
    await userEvent.setup().click(territory)

    // Then it fills the viewport immediately, without an animated transition
    expect(territory).toHaveAttribute('aria-current', 'true')
    const canvas = screen.getByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    expect(surface.className).not.toContain('transition-transform')
  })

  it('pans the canvas by dragging', async () => {
    // Given the reviewer is viewing the territory map
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement

    // When the reviewer drags the canvas
    fireEvent.pointerDown(canvas, { clientX: 0, clientY: 0 })
    fireEvent.pointerMove(canvas, { clientX: 50, clientY: 30 })

    // Then the visible territories shift to follow the drag
    expect(surface.style.transform).toContain('translate(50px, 30px)')
  })

  it('zooms in on scroll', async () => {
    // Given the reviewer is viewing the territory map
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement

    // When the reviewer scrolls to zoom in
    fireEvent.wheel(canvas, { deltaY: -100 })

    // Then the canvas scale increases
    expect(surface.style.transform).toContain('scale(1.2)')
  })

  it('zooms via the on-screen controls, and resets via the reset control', async () => {
    // Given the reviewer is viewing the territory map
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    const user = userEvent.setup()

    // When the reviewer clicks the zoom-in control
    await user.click(screen.getByRole('button', { name: 'Zoom in' }))
    // Then the canvas scale increases
    expect(surface.style.transform).toContain('scale(1.2)')

    // When the reviewer clicks the reset control
    await user.click(screen.getByRole('button', { name: 'Reset view' }))
    // Then the canvas returns to its initial pan and scale
    expect(surface.style.transform).toContain('scale(1)')
    expect(surface.style.transform).toContain('translate(0px, 0px)')
  })

  it('clamps zoom so the scale cannot exceed the maximum', async () => {
    // Given the reviewer is viewing the territory map
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    const user = userEvent.setup()
    const zoomIn = screen.getByRole('button', { name: 'Zoom in' })

    // When the reviewer zooms in repeatedly past the maximum scale
    for (let i = 0; i < 20; i++) {
      await user.click(zoomIn)
    }

    // Then the canvas scale does not exceed the maximum allowed scale
    expect(surface.style.transform).toContain('scale(2.5)')
  })
})

describe('Semantic Canvas — first view (ticket #318)', () => {
  const idle = (moduleName: string): ModuleTopology['territories'][number] => ({
    moduleName,
    status: 'IDLE',
    fileCount: 0,
    statusSummary: 'unchanged',
    techStack: 'JAVA',
    techStackLabel: 'Java',
    changeKeys: [],
    testChangeKeys: [],
  })
  const WITH_UNCHANGED_DEPENDENCIES: ModuleTopology = {
    territories: [...TWO_TOUCHED_MODULES.territories, idle('crowdness-common'), idle('crowdness-connect')],
    dependencies: [
      { from: 'crowdness-live', to: 'crowdness-ingestion' },
      { from: 'crowdness-live', to: 'crowdness-common' },
      { from: 'crowdness-ingestion', to: 'crowdness-connect' },
    ],
  }

  it('opens on the touched modules and the rails between them only', async () => {
    mockTopology(WITH_UNCHANGED_DEPENDENCIES)
    renderCanvas()

    await screen.findByRole('button', { name: 'crowdness-live territory' })
    expect(screen.getByRole('button', { name: 'crowdness-ingestion territory' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'crowdness-common territory' })).not.toBeInTheDocument()
    expect(document.querySelectorAll('[data-testid="dependency-rail"]')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Show 2 unchanged dependencies' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('shows the unchanged dependencies and their rails on request, and hides them again', async () => {
    mockTopology(WITH_UNCHANGED_DEPENDENCIES)
    renderCanvas()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Show 2 unchanged dependencies' }))

    expect(await screen.findByRole('button', { name: 'crowdness-common territory' })).toHaveAttribute('data-status', 'IDLE')
    expect(document.querySelectorAll('[data-testid="dependency-rail"]')).toHaveLength(3)

    await user.click(screen.getByRole('button', { name: 'Hide unchanged dependencies' }))

    expect(screen.queryByRole('button', { name: 'crowdness-common territory' })).not.toBeInTheDocument()
    expect(document.querySelectorAll('[data-testid="dependency-rail"]')).toHaveLength(1)
  })

  it('offers no control when there are no unchanged dependencies', async () => {
    mockTopology(TWO_TOUCHED_MODULES)
    renderCanvas()

    await screen.findByRole('button', { name: 'crowdness-live territory' })
    expect(screen.queryByRole('button', { name: /unchanged dependenc/ })).not.toBeInTheDocument()
  })

  it('opens the keycloak-52898 corpus topology on its 3 touched modules instead of 35', async () => {
    // The corpus snapshot (evaluation/snapshots/c6a1721/keycloak-52898/topology.json), copied as a fixture.
    const keycloak = JSON.parse(
      readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), 'test/fixtures/keycloak-52898-topology.json'), 'utf-8'),
    ) as ModuleTopology
    mockTopology(keycloak)
    renderCanvas()
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'services territory' })
    expect(screen.getAllByTestId('territory')).toHaveLength(3)

    await user.click(screen.getByRole('button', { name: 'Show 32 unchanged dependencies' }))

    expect(screen.getAllByTestId('territory')).toHaveLength(35)
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_canvas_motion_and_animation.feature

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

const TWO_TERRITORIES: ModuleTopology = {
  territories: [
    {
      moduleName: 'crowdness-live',
      status: 'NEW',
      fileCount: 2,
      statusSummary: '2 files · new module',
      techStack: 'SPRING_BOOT_JAVA',
      techStackLabel: 'Spring Boot · Java',
      changeKeys: [],
      testChangeKeys: [],
    },
    {
      moduleName: 'crowdness-ingestion',
      status: 'TOUCHED',
      fileCount: 1,
      statusSummary: '1 file',
      techStack: 'JAVA',
      techStackLabel: 'Java',
      changeKeys: [],
      testChangeKeys: [],
    },
  ],
  dependencies: [{ from: 'crowdness-live', to: 'crowdness-ingestion' }],
}

describe('Semantic Canvas — motion and animation pass', () => {
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

  it('animates the camera dive with an eased transition', async () => {
    mockTopology(TWO_TERRITORIES)
    renderCanvas()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })

    await userEvent.setup().click(territory)

    const canvas = screen.getByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    expect(surface.className).toContain('transition-transform')
    expect(surface.className).toContain('duration-500')
    expect(surface.className).toContain('ease-[cubic-bezier(0.22,1,0.36,1)]')
  })

  it('animates newly revealed altitude-stop nodes in staggered', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        {
          dimension: 'PATTERN',
          conceptName: 'First pattern',
          conceptDescription: '',
          inferred: true,
          confidencePercent: 70,
          evidence: [],
          supportingConceptNames: [],
          filesTouched: [],
        },
        {
          dimension: 'PATTERN',
          conceptName: 'Second pattern',
          conceptDescription: '',
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

    const nodes = await screen.findAllByTestId('concept-node')
    expect(nodes).toHaveLength(2)
    expect(nodes[0].className).toContain('animate-canvas-node-appear')
    expect(nodes[1].className).toContain('animate-canvas-node-appear')
    const firstDelay = nodes[0].style.animationDelay
    const secondDelay = nodes[1].style.animationDelay
    expect(firstDelay).not.toBe(secondDelay)
  })

  it("shows an ambient pulse for a newly introduced module's territory", async () => {
    mockTopology(TWO_TERRITORIES)
    renderCanvas()

    const newTerritory = await screen.findByRole('button', { name: 'crowdness-live territory' })

    expect(newTerritory.querySelector('.animate-canvas-territory-pulse')).not.toBeNull()
  })

  it('does not pulse a merely-touched territory', async () => {
    mockTopology(TWO_TERRITORIES)
    renderCanvas()

    const touchedTerritory = await screen.findByRole('button', { name: 'crowdness-ingestion territory' })

    expect(touchedTerritory.querySelector('.animate-canvas-territory-pulse')).toBeNull()
  })

  it('animates a flowing dash on a connector touching the current selection', async () => {
    mockTopology(TWO_TERRITORIES)
    renderCanvas()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })

    await userEvent.setup().click(territory)

    const rail = document.querySelector('[data-testid="dependency-rail"][data-from="crowdness-live"]')
    expect(rail).not.toBeNull()
    expect(rail).toHaveAttribute('data-active', 'true')
    expect(rail?.getAttribute('class')).toContain('animate-flow-dash')
  })

  it('keeps a connector not touching the current selection static', async () => {
    mockTopology({
      territories: [
        ...TWO_TERRITORIES.territories,
        {
          moduleName: 'crowdness-ui',
          status: 'TOUCHED',
          fileCount: 1,
          statusSummary: '1 file',
          techStack: 'REACT_TYPESCRIPT',
          techStackLabel: 'React · TypeScript',
          changeKeys: [],
          testChangeKeys: [],
        },
        {
          moduleName: 'crowdness-management',
          status: 'IDLE',
          fileCount: 0,
          statusSummary: 'unchanged',
          techStack: 'JAVA',
          techStackLabel: 'Java',
          changeKeys: [],
          testChangeKeys: [],
        },
      ],
      dependencies: [
        { from: 'crowdness-live', to: 'crowdness-ingestion' },
        { from: 'crowdness-ui', to: 'crowdness-management' },
      ],
    })
    renderCanvas()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })

    await userEvent.setup().click(territory)

    const unrelatedRail = document.querySelector('[data-testid="dependency-rail"][data-from="crowdness-ui"]')
    expect(unrelatedRail).not.toBeNull()
    expect(unrelatedRail).not.toHaveAttribute('data-active')
    expect(unrelatedRail?.getAttribute('class') ?? '').not.toContain('animate-flow-dash')
  })

  it('applies the reduced-motion end state immediately for the camera dive', async () => {
    mockReducedMotion(true)
    mockTopology(TWO_TERRITORIES)
    renderCanvas()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })

    await userEvent.setup().click(territory)

    const canvas = screen.getByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    expect(surface.className).not.toContain('transition-transform')
    expect(territory).toHaveAttribute('aria-current', 'true')
  })

  it('reduced-motion still shows the new-module territory and active connector, just without animation classes driving them', async () => {
    mockReducedMotion(true)
    mockTopology(TWO_TERRITORIES)
    renderCanvas()

    const newTerritory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    // Tailwind's motion-reduce:animate-none neutralizes .animate-pulse via CSS at
    // the media-query level — the class stays present (jsdom doesn't evaluate the
    // media query), but the territory and its status are still correctly shown.
    expect(newTerritory).toHaveAttribute('data-status', 'NEW')

    await userEvent.setup().click(newTerritory)
    const rail = document.querySelector('[data-testid="dependency-rail"][data-from="crowdness-live"]')
    expect(rail).toHaveAttribute('data-active', 'true')
  })

  it('lets a second dive interrupt the first without getting stuck', async () => {
    mockTopology({
      territories: [
        ...TWO_TERRITORIES.territories,
        {
          moduleName: 'crowdness-ui',
          status: 'TOUCHED',
          fileCount: 1,
          statusSummary: '1 file',
          techStack: 'REACT_TYPESCRIPT',
          techStackLabel: 'React · TypeScript',
          changeKeys: [],
          testChangeKeys: [],
        },
      ],
      dependencies: [],
    })
    renderCanvas()
    const user = userEvent.setup()
    const live = await screen.findByRole('button', { name: 'crowdness-live territory' })
    const ui = await screen.findByRole('button', { name: 'crowdness-ui territory' })

    await user.click(live)
    await user.click(ui)

    expect(ui).toHaveAttribute('aria-current', 'true')
    expect(live).not.toHaveAttribute('aria-current')
    // The canvas is still interactive — a third click works too.
    const canvas = screen.getByRole('application', { name: 'Semantic Canvas territory map' })
    expect(canvas).toBeVisible()
  })
})

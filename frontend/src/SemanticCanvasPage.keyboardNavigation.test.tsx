import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'
import { pressShortcut } from './test/keyboardShortcuts'

// Traces frontend/src/test/resources/features/ui_first_experience/keyboard_navigation_and_focus.feature
// and frontend/src/test/resources/features/ui_first_experience/keyboard_semantic_canvas_navigation.feature
// (and the touched-item navigation scenarios of keyboard_review_navigation.feature)

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
      status: 'TOUCHED',
      fileCount: 5,
      statusSummary: '5 files',
      techStack: 'SPRING_BOOT_JAVA',
      techStackLabel: 'Spring Boot · Java',
      changeKeys: ['change-key-1'],
    },
    {
      moduleName: 'crowdness-ingestion',
      status: 'TOUCHED',
      fileCount: 3,
      statusSummary: '3 files',
      techStack: 'JAVA',
      techStackLabel: 'Java',
      changeKeys: [],
    },
  ],
  dependencies: [],
}

const STRUCTURE_TWO_FILES: SemanticProfile = {
  dimensions: [
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Add validation',
      conceptDescription: 'Description',
      inferred: false,
      confidencePercent: 100,
      evidence: [],
      supportingConceptNames: [],
      filesTouched: ['PaymentValidator.java', 'PaymentGateway.java'],
    },
  ],
}

const SAME_CONCEPT_AT_PATTERN_AND_ARCHITECTURE: SemanticProfile = {
  dimensions: [
    {
      dimension: 'PATTERN',
      conceptName: 'Idempotent recovery',
      conceptDescription: 'Description',
      inferred: true,
      confidencePercent: 70,
      evidence: [],
      supportingConceptNames: [],
    },
    {
      dimension: 'ARCHITECTURE',
      conceptName: 'Idempotent recovery',
      conceptDescription: 'Description',
      inferred: true,
      confidencePercent: 70,
      evidence: [],
      supportingConceptNames: [],
    },
  ],
}

describe('Keyboard navigation and focus', () => {
  it('reaches a territory, and the canvas zoom controls, without the mouse', async () => {
    mockTopology(TWO_TERRITORIES)
    renderCanvas()

    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    territory.focus()
    expect(document.activeElement).toBe(territory)

    const zoomIn = screen.getByRole('button', { name: 'Zoom in' })
    zoomIn.focus()
    expect(document.activeElement).toBe(zoomIn)
  })

  it('declares a visible focus style on a keyboard-reachable territory', async () => {
    // jsdom doesn't render real layout/CSS, so this checks the element declares a
    // focus-visible style rule (this codebase's existing convention, e.g.
    // ChangeDetailPage's `focus:border-accent`) rather than computed pixels.
    mockTopology(TWO_TERRITORIES)
    renderCanvas()

    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })

    expect(territory.className).toMatch(/focus(-visible)?:/)
  })

  it('moving to the next semantic element moves focus to the next territory', async () => {
    mockTopology(TWO_TERRITORIES)
    renderCanvas()
    const first = await screen.findByRole('button', { name: 'crowdness-live territory' })
    first.focus()

    pressShortcut('moveToNextSemanticElement', screen.getByTestId('semantic-canvas'))

    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'crowdness-ingestion territory' }))
  })

  it('a reviewer can leave the canvas application region with the keyboard', async () => {
    mockTopology(TWO_TERRITORIES)
    renderCanvas()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    territory.focus()

    pressShortcut('leaveRegion', canvas)

    expect(document.activeElement).not.toBe(territory)
    expect(canvas.contains(document.activeElement)).toBe(false)
  })

  it('selecting a focused semantic element opens the detail drawer', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', STRUCTURE_TWO_FILES)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)

    const [conceptNode] = await screen.findAllByTestId('file-node')
    conceptNode.focus()
    pressShortcut('selectElement', conceptNode)

    expect(await screen.findByRole('dialog', { name: 'Detail drawer' })).toBeVisible()
  })

  it('entering a territory dives into it and moves focus inside', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', STRUCTURE_TWO_FILES)
    renderCanvas()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    territory.focus()

    pressShortcut('enterElementContext', territory)

    await screen.findAllByTestId('file-node')
    const rail = screen.getByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    expect(rail).toBeVisible()
    expect(document.activeElement).not.toBe(territory)
    expect(document.activeElement === document.body).toBe(false)
  })

  it('leaving a territory returns to the territory map and refocuses it', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', STRUCTURE_TWO_FILES)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })

    pressShortcut('leaveElementContext')

    expect(await screen.findByRole('application', { name: 'Semantic Canvas territory map' })).toBeVisible()
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'crowdness-live territory' }))
  })

  it('moves between sibling file nodes within the same territory', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', STRUCTURE_TWO_FILES)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))

    const files = await screen.findAllByTestId('file-node')
    files[0].focus()
    pressShortcut('nextSibling')

    expect(document.activeElement).toBe(files[1])
  })

  it('moves focus to the previous touched item in the current territory', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', STRUCTURE_TWO_FILES)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))
    const files = await screen.findAllByTestId('file-node')
    files[1].focus()

    pressShortcut('previousItem', files[1])

    expect(document.activeElement).toBe(files[0])
  })

  it('stays on the last touched item — there is no next item after it', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', STRUCTURE_TWO_FILES)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))
    const files = await screen.findAllByTestId('file-node')
    const last = files[files.length - 1]
    last.focus()

    pressShortcut('nextItem', last)

    expect(document.activeElement).toBe(last)
  })

  it('entering a file node backed by a Change opens its detail view with the keyboard', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', STRUCTURE_TWO_FILES)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))
    const fileNode = (await screen.findAllByTestId('file-node'))[0]
    fileNode.focus()

    pressShortcut('enterItem', fileNode)

    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    expect(drawer).toBeVisible()
    expect(drawer.contains(document.activeElement)).toBe(true)
  })

  it('returning from a Change detail view returns focus to that item on the canvas', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', STRUCTURE_TWO_FILES)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))
    const fileNode = (await screen.findAllByTestId('file-node'))[0]
    fileNode.focus()
    pressShortcut('enterItem', fileNode)
    await screen.findByRole('dialog', { name: 'Detail drawer' })

    pressShortcut('returnToPreviousContext')

    expect(screen.queryByRole('dialog', { name: 'Detail drawer' })).not.toBeInTheDocument()
    expect(document.activeElement).toBe(fileNode)
  })

  it('jumps to a higher-level representation of the focused component with the keyboard', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', SAME_CONCEPT_AT_PATTERN_AND_ARCHITECTURE)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Pattern/ }))
    const patternNode = await screen.findByTestId('concept-node')
    patternNode.focus()

    pressShortcut('higherLevel', patternNode)

    expect(within(rail).getByRole('button', { name: /Architecture/ })).toHaveAttribute('aria-current', 'true')
    expect(document.activeElement).toHaveAttribute('data-item-label', 'Idempotent recovery')
  })

  it('jumps to a lower-level representation of the focused territory with the keyboard', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', SAME_CONCEPT_AT_PATTERN_AND_ARCHITECTURE)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Architecture/ }))
    const architectureNode = await screen.findByTestId('concept-node')
    architectureNode.focus()

    pressShortcut('lowerLevel', architectureNode)

    expect(within(rail).getByRole('button', { name: /Pattern/ })).toHaveAttribute('aria-current', 'true')
    expect(document.activeElement).toHaveAttribute('data-item-label', 'Idempotent recovery')
  })
})

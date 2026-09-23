import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'
import { pressShortcut } from './test/keyboardShortcuts'

// Traces frontend/src/test/resources/features/ui_first_experience/keyboard_zoom.feature

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
      status: 'TOUCHED',
      fileCount: 5,
      statusSummary: '5 files',
      techStack: 'SPRING_BOOT_JAVA',
      techStackLabel: 'Spring Boot · Java',
      changeKeys: [],
      testChangeKeys: [],
    },
  ],
  dependencies: [],
}

const CAPABILITY_PROFILE: SemanticProfile = {
  dimensions: [
    {
      dimension: 'INTENT',
      conceptName: 'Reduce coupling',
      conceptDescription: 'Description',
      inferred: true,
      confidencePercent: 70,
      evidence: [],
      supportingConceptNames: [],
    },
    {
      dimension: 'RESPONSIBILITY',
      conceptName: 'Move Responsibility',
      conceptDescription: 'Description',
      inferred: true,
      confidencePercent: 70,
      evidence: [],
      supportingConceptNames: [],
    },
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Add validation',
      conceptDescription: 'Description',
      inferred: false,
      confidencePercent: 100,
      evidence: [],
      supportingConceptNames: [],
      filesTouched: ['PaymentValidator.java'],
    },
  ],
}

async function diveInAndReachCapabilityFlow() {
  const user = userEvent.setup()
  const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
  await user.click(territory)
  const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
  await user.click(within(rail).getByRole('button', { name: /Capability/ }))
  return rail
}

function currentStop(rail: HTMLElement) {
  return rail.querySelector('[aria-current="true"]')?.textContent
}

describe('Keyboard control of Semantic Canvas zoom', () => {
  it('zooms in with the keyboard to a stop closer than the current one', async () => {
    mockTopology(ONE_TERRITORY)
    mockModuleProfile('crowdness-live', CAPABILITY_PROFILE)
    renderCanvas()
    const rail = await diveInAndReachCapabilityFlow()
    expect(currentStop(rail)).toContain('Capability')

    pressShortcut('zoomIn')

    expect(currentStop(rail)).not.toContain('Capability')
  })

  it('zooms out with the keyboard to a stop farther out than the current one', async () => {
    mockTopology(ONE_TERRITORY)
    mockModuleProfile('crowdness-live', CAPABILITY_PROFILE)
    renderCanvas()
    const rail = await diveInAndReachCapabilityFlow()

    pressShortcut('zoomOut')

    expect(currentStop(rail)).not.toContain('Capability')
  })

  it('resets/fits the view with the keyboard', async () => {
    mockTopology(ONE_TERRITORY)
    renderCanvas()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    const defaultTransform = surface.style.transform
    const zoomIn = screen.getByRole('button', { name: 'Zoom in' })
    await userEvent.setup().click(zoomIn)
    expect(surface.style.transform).not.toBe(defaultTransform)

    pressShortcut('resetView')

    expect(surface.style.transform).toBe(defaultTransform)
  })

  it('returns to the previous semantic level with the keyboard', async () => {
    mockTopology(ONE_TERRITORY)
    mockModuleProfile('crowdness-live', CAPABILITY_PROFILE)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Capability/ }))

    pressShortcut('previousZoomLevel')

    expect(currentStop(rail)).not.toContain('Capability')
  })

  it('jumps directly to a specific zoom-altitude stop with the keyboard', async () => {
    mockTopology(ONE_TERRITORY)
    mockModuleProfile('crowdness-live', CAPABILITY_PROFILE)
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })

    within(rail).getByRole('button', { name: /Capability/ }).focus()
    pressShortcut('selectElement', rail)

    expect(currentStop(rail)).toContain('Capability')
  })

  it('every keyboard zoom action succeeds with no mouse wheel, trackpad, or pinch input at all', async () => {
    mockTopology(ONE_TERRITORY)
    renderCanvas()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    const defaultTransform = surface.style.transform

    pressShortcut('zoomIn')
    expect(surface.style.transform).not.toBe(defaultTransform)

    pressShortcut('resetView')
    expect(surface.style.transform).toBe(defaultTransform)
  })
})

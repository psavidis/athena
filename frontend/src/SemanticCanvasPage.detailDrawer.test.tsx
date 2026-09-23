import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_canvas_detail_drawer.feature

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

async function diveIntoLive() {
  const user = userEvent.setup()
  const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
  await user.click(territory)
  return user
}

describe('Semantic Canvas — sliding detail drawer', () => {
  it('is not visible by default', async () => {
    mockTopology(TWO_TERRITORIES)
    renderCanvas()

    expect(screen.queryByRole('dialog', { name: 'Detail drawer' })).not.toBeInTheDocument()
  })

  it("opens with a concept node's description and linked chips when selected", async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        {
          dimension: 'PATTERN',
          conceptName: 'Idempotent recovery',
          conceptDescription: 'Retrying this operation is always safe.',
          inferred: true,
          confidencePercent: 70,
          evidence: [],
          supportingConceptNames: [],
          filesTouched: ['PaymentValidator.java'],
        },
      ],
    })
    renderCanvas()
    await diveIntoLive()

    const user = userEvent.setup()
    await user.click(await screen.findByText('Idempotent recovery'))

    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    expect(drawer).toHaveTextContent('Retrying this operation is always safe.')
    expect(screen.getByTestId('drawer-file-chip')).toHaveTextContent('PaymentValidator.java')
  })

  it('jumps the canvas to the file when a linked chip is clicked', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        {
          dimension: 'PATTERN',
          conceptName: 'Idempotent recovery',
          conceptDescription: 'Description',
          inferred: true,
          confidencePercent: 70,
          evidence: [],
          supportingConceptNames: [],
          filesTouched: ['PaymentValidator.java'],
        },
        {
          dimension: 'STRUCTURAL',
          conceptName: 'Add recovery check',
          conceptDescription: 'Description',
          inferred: false,
          confidencePercent: 100,
          evidence: [],
          supportingConceptNames: [],
          filesTouched: ['PaymentValidator.java'],
        },
      ],
    })
    renderCanvas()
    await diveIntoLive()
    const user = userEvent.setup()
    await user.click(await screen.findByText('Idempotent recovery'))

    await user.click(await screen.findByTestId('drawer-file-chip'))

    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    expect(rail.querySelector('[aria-current="true"]')).toHaveTextContent('Structure')
  })

  it("opens with a file node's diff and an evidence statement when selected", async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', {
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
    })
    server.use(
      http.get('/api/review/changes/change-key-1', () =>
        HttpResponse.json({
          changeKey: 'change-key-1',
          category: 'BEHAVIORAL',
          kind: 'ADD_SYMBOL',
          description: 'Add recovery check',
          symbols: [],
          files: ['PaymentValidator.java'],
          diff: '+ public void recover() {}',
        }),
      ),
    )
    renderCanvas()
    await diveIntoLive()
    const user = userEvent.setup()
    await user.click(await screen.findByText('PaymentValidator.java'))

    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    // The diff renders the leading +/- marker and the code text as separate
    // (syntax-highlighted) elements, so match on the one diff line whose own
    // combined text is the expected content, not an ancestor's.
    expect(
      await screen.findByText(
        (_, element) => element?.tagName === 'DIV' && element.parentElement?.tagName === 'PRE' && element.textContent === '+ public void recover() {}',
      ),
    ).toBeVisible()
    expect(drawer.querySelector('[data-testid="evidence-statement"]')).toHaveTextContent('Idempotent recovery')
  })

  it('shows a "no diff recorded" fallback instead of rendering nothing when no matching Change is found', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-ingestion', {
      dimensions: [
        {
          dimension: 'STRUCTURAL',
          conceptName: 'Add ingestion check',
          conceptDescription: 'Description',
          inferred: false,
          confidencePercent: 100,
          evidence: [],
          supportingConceptNames: [],
          filesTouched: ['IngestionWorker.java'],
        },
      ],
    })
    renderCanvas()
    const user = userEvent.setup()
    const ingestionTerritory = await screen.findByRole('button', { name: 'crowdness-ingestion territory' })
    await user.click(ingestionTerritory)

    // crowdness-ingestion's changeKeys is empty in the fixture, so no Change can be found for the file.
    await user.click(await screen.findByText('IngestionWorker.java'))

    expect(await screen.findByText('No diff recorded for this Change.')).toBeVisible()
  })

  it('shows a footprint summary instead of a diff for the PR-overview node', async () => {
    mockTopology(TWO_TERRITORIES)
    renderCanvas()

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'PR overview' }))

    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    expect(screen.getByTestId('footprint-summary')).toBeVisible()
    expect(drawer.querySelector('pre')).not.toBeInTheDocument()
  })

  it('closes the drawer without resetting the camera', async () => {
    mockTopology(TWO_TERRITORIES)
    mockModuleProfile('crowdness-live', {
      dimensions: [
        {
          dimension: 'PATTERN',
          conceptName: 'Idempotent recovery',
          conceptDescription: 'Description',
          inferred: true,
          confidencePercent: 70,
          evidence: [],
          supportingConceptNames: [],
          filesTouched: [],
        },
      ],
    })
    renderCanvas()
    await diveIntoLive()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    const scaleBeforeDrawer = surface.style.transform

    const user = userEvent.setup()
    await user.click(await screen.findByText('Idempotent recovery'))
    await user.click(await screen.findByRole('button', { name: 'Close detail drawer' }))

    expect(screen.queryByRole('dialog', { name: 'Detail drawer' })).not.toBeInTheDocument()
    // The camera zoomed in for the concept selection itself (ticket #130's per-node zoom) —
    // closing the drawer must not additionally move it, so it stays at that same scale.
    expect(surface.style.transform).not.toBe(scaleBeforeDrawer)
    expect(surface.style.transform).toContain(`scale(${1.6})`)
  })

  it('does not move or resize the canvas underneath it when the drawer opens', async () => {
    mockTopology(TWO_TERRITORIES)
    renderCanvas()
    const canvas = await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    const surface = canvas.firstElementChild as HTMLElement
    fireEventPan(canvas)
    const transformBeforeDrawer = surface.style.transform

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'PR overview' }))

    expect(await screen.findByRole('dialog', { name: 'Detail drawer' })).toBeVisible()
    expect(surface.style.transform).toBe(transformBeforeDrawer)
  })
})

function fireEventPan(canvas: HTMLElement) {
  fireEvent.pointerDown(canvas, { clientX: 0, clientY: 0 })
  fireEvent.pointerMove(canvas, { clientX: 40, clientY: 20 })
  fireEvent.pointerUp(canvas)
}

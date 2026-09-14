import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ChangeDetail, ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_canvas_file_first_mode.feature

function mockTopology(topology: ModuleTopology) {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(topology)))
}

function mockChangeDetail(changeKey: string, detail: ChangeDetail) {
  server.use(http.get(`/api/review/changes/${changeKey}`, () => HttpResponse.json(detail)))
}

function mockModuleProfile(moduleName: string, profile: SemanticProfile) {
  server.use(http.get(`/api/review/modules/${moduleName}/semantic-profile`, () => HttpResponse.json(profile)))
}

function renderCanvas() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticCanvasPage onNotConnected={vi.fn()} onNoPullRequestSelected={vi.fn()} />
    </QueryClientProvider>,
  )
}

const TWO_MODULE_TOPOLOGY: ModuleTopology = {
  territories: [
    {
      moduleName: 'crowdness-live',
      status: 'TOUCHED',
      fileCount: 2,
      statusSummary: '2 files',
      techStack: 'SPRING_BOOT_JAVA',
      techStackLabel: 'Spring Boot · Java',
      changeKeys: ['change-1'],
    },
    {
      moduleName: 'crowdness-ingestion',
      status: 'TOUCHED',
      fileCount: 1,
      statusSummary: '1 file',
      techStack: 'JAVA',
      techStackLabel: 'Java',
      changeKeys: ['change-2'],
    },
  ],
  dependencies: [],
}

function baseChange(overrides: Partial<ChangeDetail>): ChangeDetail {
  return {
    changeKey: 'change-1',
    category: 'BEHAVIORAL',
    kind: 'CHANGE_METHOD_SIGNATURE',
    description: 'A change',
    symbols: [],
    files: [],
    diff: '',
    ...overrides,
  }
}

async function switchToFileFirst() {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'File-First' }))
  return user
}

describe('Semantic Canvas — File-First review mode', () => {
  it('replaces the canvas viewport with a flat file list when switched on', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail('change-1', baseChange({ changeKey: 'change-1', files: ['OrderService.java'] }))
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: ['IngestionWorker.java'] }))
    renderCanvas()

    await switchToFileFirst()

    expect(await screen.findByRole('region', { name: 'File-First file list' })).toBeVisible()
    expect(screen.queryByRole('application', { name: 'Semantic Canvas territory map' })).not.toBeInTheDocument()
  })

  it('groups files by module with a real file count', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail('change-1', baseChange({ changeKey: 'change-1', files: ['A.java', 'B.java'] }))
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: ['C.java'] }))
    renderCanvas()
    await switchToFileFirst()

    expect(await screen.findByText('crowdness-live')).toBeVisible()
    const liveGroup = screen.getByText('crowdness-live').closest('div')!
    expect(within(liveGroup).getByText('2 files')).toBeVisible()
    const ingestionGroup = screen.getByText('crowdness-ingestion').closest('div')!
    expect(within(ingestionGroup).getByText('1 files')).toBeVisible()
  })

  it('collapses and expands a module group independently of the other', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail('change-1', baseChange({ changeKey: 'change-1', files: ['A.java'] }))
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: ['C.java'] }))
    renderCanvas()
    const user = await switchToFileFirst()
    await screen.findByText('A.java')

    await user.click(screen.getByText('crowdness-live'))

    expect(screen.queryByText('A.java')).not.toBeInTheDocument()
    expect(screen.getByText('C.java')).toBeVisible()
  })

  it('shows a distinct config icon for a config/build file, generic icon otherwise', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail(
      'change-1',
      baseChange({ changeKey: 'change-1', files: ['crowdness-live/application.yml', 'OrderService.java'] }),
    )
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: [] }))
    renderCanvas()
    await switchToFileFirst()

    const configRow = (await screen.findByText('crowdness-live/application.yml')).closest('button')!
    const prodRow = screen.getByText('OrderService.java').closest('button')!
    expect(within(configRow).getByTestId('config-icon')).toBeVisible()
    expect(within(prodRow).getByTestId('file-icon')).toBeVisible()
  })

  it('filters the list by a search term matching the file path', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail('change-1', baseChange({ changeKey: 'change-1', files: ['OrderService.java', 'PaymentValidator.java'] }))
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: [] }))
    renderCanvas()
    const user = await switchToFileFirst()
    await screen.findByText('OrderService.java')

    await user.type(screen.getByRole('searchbox', { name: 'Search files' }), 'Payment')

    expect(screen.queryByText('OrderService.java')).not.toBeInTheDocument()
    expect(screen.getByText('PaymentValidator.java')).toBeVisible()
  })

  it('filters the list by a search term matching the module name', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail('change-1', baseChange({ changeKey: 'change-1', files: ['OrderService.java'] }))
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: ['IngestionWorker.java'] }))
    renderCanvas()
    const user = await switchToFileFirst()
    await screen.findByText('OrderService.java')

    await user.type(screen.getByRole('searchbox', { name: 'Search files' }), 'ingestion')

    expect(screen.queryByText('OrderService.java')).not.toBeInTheDocument()
    expect(screen.getByText('IngestionWorker.java')).toBeVisible()
  })

  it('applies the "New code" quick filter to show only added files', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail(
      'change-1',
      baseChange({ changeKey: 'change-1', kind: 'ADD_CLASS', files: ['NewFile.java'] }),
    )
    mockChangeDetail(
      'change-2',
      baseChange({ changeKey: 'change-2', kind: 'CHANGE_METHOD_SIGNATURE', files: ['ModifiedFile.java'] }),
    )
    renderCanvas()
    const user = await switchToFileFirst()
    await screen.findByText('NewFile.java')

    await user.click(screen.getByRole('button', { name: 'New code' }))

    expect(screen.getByText('NewFile.java')).toBeVisible()
    expect(screen.queryByText('ModifiedFile.java')).not.toBeInTheDocument()
  })

  it('applies the "Has context" quick filter to show only files with a matching canvas node', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail('change-1', baseChange({ changeKey: 'change-1', files: ['HasContext.java'] }))
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: ['NoContext.java'] }))
    mockModuleProfile('crowdness-live', {
      dimensions: [
        {
          dimension: 'STRUCTURAL',
          conceptName: 'Some change',
          conceptDescription: '',
          inferred: false,
          confidencePercent: 100,
          evidence: [],
          supportingConceptNames: [],
          filesTouched: ['HasContext.java'],
        },
      ],
    })
    mockModuleProfile('crowdness-ingestion', { dimensions: [] })
    renderCanvas()
    const user = await switchToFileFirst()
    await screen.findByText('HasContext.java')

    await user.click(screen.getByRole('button', { name: 'Has context' }))

    expect(await screen.findByText('HasContext.java')).toBeVisible()
    expect(screen.queryByText('NoContext.java')).not.toBeInTheDocument()
  })

  it('opens the diff directly in the detail drawer when a file row is clicked', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail('change-1', baseChange({ changeKey: 'change-1', files: ['OrderService.java'], diff: '+ added line' }))
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: [] }))
    renderCanvas()
    const user = await switchToFileFirst()
    await screen.findByText('OrderService.java')

    await user.click(screen.getByTestId('file-first-row'))

    expect(await screen.findByRole('dialog', { name: 'Detail drawer' })).toBeVisible()
    expect(await screen.findByText('+ added line')).toBeVisible()
  })

  it('shows no "Explain this" action for a file with no matching canvas node', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail('change-1', baseChange({ changeKey: 'change-1', files: ['GeneratedConfig.java'] }))
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: [] }))
    mockModuleProfile('crowdness-live', { dimensions: [] })
    mockModuleProfile('crowdness-ingestion', { dimensions: [] })
    renderCanvas()
    await switchToFileFirst()
    const row = (await screen.findByText('GeneratedConfig.java')).closest('li')!

    expect(within(row).queryByTestId('explain-this')).not.toBeInTheDocument()
  })

  it('"Explain this" switches to Contextual mode and lands on the Structure node for that file', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail('change-1', baseChange({ changeKey: 'change-1', files: ['PaymentValidator.java'] }))
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: [] }))
    mockModuleProfile('crowdness-live', {
      dimensions: [
        {
          dimension: 'STRUCTURAL',
          conceptName: 'Add validation',
          conceptDescription: '',
          inferred: false,
          confidencePercent: 100,
          evidence: [],
          supportingConceptNames: [],
          filesTouched: ['PaymentValidator.java'],
        },
      ],
    })
    mockModuleProfile('crowdness-ingestion', { dimensions: [] })
    renderCanvas()
    const user = await switchToFileFirst()
    const row = (await screen.findByText('PaymentValidator.java')).closest('li')!

    await user.click(within(row).getByTestId('explain-this'))

    expect(await screen.findByRole('application', { name: 'Semantic Canvas territory map' })).toBeVisible()
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    expect(within(rail).getByRole('button', { name: /Structure/ })).toHaveAttribute('aria-current', 'true')
  })

  it('returns to the territory map when switching back to Contextual mode', async () => {
    mockTopology(TWO_MODULE_TOPOLOGY)
    mockChangeDetail('change-1', baseChange({ changeKey: 'change-1', files: [] }))
    mockChangeDetail('change-2', baseChange({ changeKey: 'change-2', files: [] }))
    renderCanvas()
    const user = await switchToFileFirst()
    await screen.findByRole('region', { name: 'File-First file list' })

    await user.click(screen.getByRole('button', { name: 'Contextual' }))

    expect(await screen.findByRole('application', { name: 'Semantic Canvas territory map' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'crowdness-live territory' })).toBeVisible()
  })
})

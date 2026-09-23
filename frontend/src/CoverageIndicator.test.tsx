import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import SemanticChangeExplorerPage from './SemanticChangeExplorerPage'
import type { ModuleTopology, UnrepresentedFile, UnrepresentedFiles } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/coverage_transparency_frontend_rendering.feature

const TOPOLOGY: ModuleTopology = {
  territories: [
    {
      moduleName: 'core',
      status: 'TOUCHED',
      fileCount: 5,
      statusSummary: '5 files',
      techStack: 'JAVA',
      techStackLabel: 'Java',
      changeKeys: ['change-1'],
      testChangeKeys: [],
    },
  ],
  dependencies: [],
}

function unrepresented(path: string, reasonLabel: string): UnrepresentedFile {
  return {
    path,
    status: 'MODIFIED',
    linesChanged: 2,
    hunkCount: 1,
    reason: reasonLabel === 'unsupported file type' ? 'UNSUPPORTED_FILE_TYPE' : 'NO_SEMANTIC_CHANGE',
    reasonLabel,
  }
}

function mockCoverage(coverage: UnrepresentedFiles) {
  server.use(http.get('/api/review/unrepresented-files', () => HttpResponse.json(coverage)))
}

function threeOfEightUnrepresented(): UnrepresentedFiles {
  return {
    changedFileCount: 8,
    representedFileCount: 5,
    files: [
      unrepresented('pom.xml', 'unsupported file type'),
      unrepresented('Fraction.java', 'no semantic change detected'),
      unrepresented('README.md', 'unsupported file type'),
    ],
  }
}

function renderCanvas() {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(TOPOLOGY)))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticCanvasPage pullRequest={null} onNotConnected={vi.fn()} onNoPullRequestSelected={vi.fn()} />
    </QueryClientProvider>,
  )
}

function renderExplorer() {
  server.use(
    http.get('/api/review/change-map', () =>
      HttpResponse.json({ prTitle: 'Test PR', categoryCounts: {}, changes: [], classGroups: [] }),
    ),
    http.get('/api/review/modules', () => HttpResponse.json([])),
    http.get('/api/review/change-map/:changeKey/semantic-profile', () => HttpResponse.json({ dimensions: [] })),
  )
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticChangeExplorerPage
        scope={{ kind: 'change', changeKey: 'change-1' }}
        onScopeChange={vi.fn()}
        onExitPr={vi.fn()}
        onOpenDiffView={vi.fn()}
        onOpenAiAnalysis={vi.fn()}
        onOpenSummary={vi.fn()}
        onNotConnected={vi.fn()}
        onNoPullRequestSelected={vi.fn()}
      />
    </QueryClientProvider>,
  )
}

describe('Coverage transparency', () => {
  it('the Canvas warns when some changed files are not represented', async () => {
    mockCoverage(threeOfEightUnrepresented())
    renderCanvas()

    expect(
      await screen.findByRole('button', { name: /3 of 8 changed files are not represented/ }),
    ).toBeVisible()
  })

  it('the Explorer shows the same coverage indicator', async () => {
    mockCoverage(threeOfEightUnrepresented())
    renderExplorer()

    expect(
      await screen.findByRole('button', { name: /3 of 8 changed files are not represented/ }),
    ).toBeVisible()
  })

  it('the coverage indicator lists the unrepresented files with their reasons', async () => {
    mockCoverage(threeOfEightUnrepresented())
    renderCanvas()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /changed files are not represented/ }))

    const list = screen.getByRole('list', { name: 'Unrepresented files' })
    expect(within(list).getByText('pom.xml').closest('li')).toHaveTextContent('unsupported file type')
    expect(within(list).getByText('Fraction.java').closest('li')).toHaveTextContent('no semantic change detected')
  })

  it('choosing an unrepresented file opens its raw diff', async () => {
    mockCoverage(threeOfEightUnrepresented())
    server.use(
      http.get('/api/review/raw-diff', ({ request }) =>
        HttpResponse.json({
          path: new URL(request.url).searchParams.get('path'),
          diff: '-        return a;\n+        return a / 2;',
        }),
      ),
    )
    renderCanvas()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /changed files are not represented/ }))

    await user.click(screen.getByRole('button', { name: 'Fraction.java' }))

    const dialog = await screen.findByRole('dialog', { name: 'Raw diff of Fraction.java' })
    expect(await within(dialog).findByText(/return a \/ 2;/)).toBeVisible()
  })

  it('no indicator is shown when every changed file is represented', async () => {
    mockCoverage({ changedFileCount: 4, representedFileCount: 4, files: [] })
    renderCanvas()

    await screen.findByRole('application', { name: 'Semantic Canvas territory map' })
    expect(screen.queryByRole('region', { name: 'Analysis coverage' })).not.toBeInTheDocument()
  })

  it('a review with no Changes says so and lists the changed files, each openable as a raw diff', async () => {
    const files = ['A.java', 'B.java', 'C.java', 'pom.xml', 'README.md'].map((path) =>
      unrepresented(path, path.endsWith('.java') ? 'no semantic change detected' : 'unsupported file type'),
    )
    mockCoverage({ changedFileCount: 5, representedFileCount: 0, files })
    renderCanvas()

    expect(await screen.findByText(/No semantic changes were detected in 5 changed files/)).toBeVisible()
    const list = screen.getByRole('list', { name: 'Unrepresented files' })
    expect(within(list).getAllByRole('button')).toHaveLength(5)
  })
})

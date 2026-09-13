import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ChangeMapPage from './ChangeMapPage'
import type { ChangeMap, ModuleNarrative } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/change_map_frontend_rendering.feature

// A sane default so any test that doesn't care about module narratives
// doesn't need to mock /api/review/modules itself — tests that do care call
// mockModules(...) again afterward, which overrides this one (msw uses the
// most-recently-registered matching handler).
beforeEach(() => {
  mockModules([])
})

function renderChangeMapPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onNotConnected = vi.fn()
  const onNoPullRequestSelected = vi.fn()
  const onSelectChange = vi.fn()
  const onOpenPreSubmissionSummary = vi.fn()
  const onOpenAiAnalysis = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <ChangeMapPage
        onNotConnected={onNotConnected}
        onNoPullRequestSelected={onNoPullRequestSelected}
        onSelectChange={onSelectChange}
        onOpenPreSubmissionSummary={onOpenPreSubmissionSummary}
        onOpenAiAnalysis={onOpenAiAnalysis}
      />
    </QueryClientProvider>,
  )
  return { onNotConnected, onNoPullRequestSelected, onSelectChange, onOpenPreSubmissionSummary, onOpenAiAnalysis }
}

function mockChangeMap(body: ChangeMap) {
  server.use(http.get('/api/review/change-map', () => HttpResponse.json(body)))
}

function mockModules(modules: ModuleNarrative[]) {
  server.use(http.get('/api/review/modules', () => HttpResponse.json(modules)))
}

function mockModuleNarrative(moduleName: string, narrative: ModuleNarrative) {
  server.use(http.get(`/api/review/modules/${encodeURIComponent(moduleName)}/narrative`, () => HttpResponse.json(narrative)))
}

describe('Change Map & PR Understanding View rendering', () => {
  it('shows the PR Understanding summary with the PR title and a count per category', async () => {
    // Given the reviewer has selected a PR titled "Move authentication to Account"
    // And its Change Map has 2 Behavioral, 1 Structural, 3 Mechanical, and 0 Unknown Changes
    mockChangeMap({
      prTitle: 'Move authentication to Account',
      categoryCounts: { BEHAVIORAL: 2, STRUCTURAL: 1, MECHANICAL: 3, UNKNOWN: 0 },
      changes: [],
      classGroups: [],
    })

    // When the reviewer views the Change Map page
    renderChangeMapPage()

    // Then the PR Understanding summary shows the title "Move authentication to Account"
    expect(await screen.findByText('Move authentication to Account')).toBeVisible()

    // And the summary shows 2 Behavioral, 1 Structural, 3 Mechanical, and 0 Unknown Changes
    expect(screen.getByText('Behavioral').nextElementSibling).toHaveTextContent('2')
    expect(screen.getByText('Structural').nextElementSibling).toHaveTextContent('1')
    expect(screen.getByText('Mechanical').nextElementSibling).toHaveTextContent('3')
    expect(screen.getByText('Unknown').nextElementSibling).toHaveTextContent('0')
  })

  it('lists every Change with its description, category, and review state', async () => {
    // Given the reviewer has selected a PR whose Change Map contains a Change
    // described as "Rename greet to salute" categorized as Behavioral
    mockChangeMap({
      prTitle: 'Move authentication to Account',
      categoryCounts: { BEHAVIORAL: 1, STRUCTURAL: 0, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [
        {
          id: 0,
          changeKey: 'test-change-key',
          description: 'Rename greet to salute',
          category: 'BEHAVIORAL',
          kind: 'RENAME_SYMBOL',
          reviewState: 'UNSEEN',
          occurrenceCount: 1,
          exceptionCount: 0,
        },
      ],
      classGroups: [],
    })

    // When the reviewer views the Change Map page
    renderChangeMapPage()

    // Then the Change Map shows a Change described as "Rename greet to salute"
    const description = await screen.findByText('Rename greet to salute')
    expect(description).toBeVisible()

    // And that Change is shown categorized as Behavioral
    const changeItem = description.closest('li')!
    expect(changeItem).toHaveTextContent('Behavioral')

    // And that Change is shown with review state Unseen
    expect(changeItem).toHaveTextContent('Unseen')
  })

  it('clicking a Change calls onSelectChange with its changeKey', async () => {
    mockChangeMap({
      prTitle: 'Move authentication to Account',
      categoryCounts: { BEHAVIORAL: 1, STRUCTURAL: 0, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [
        {
          id: 0,
          changeKey: 'test-change-key',
          description: 'Rename greet to salute',
          category: 'BEHAVIORAL',
          kind: 'RENAME_SYMBOL',
          reviewState: 'UNSEEN',
          occurrenceCount: 1,
          exceptionCount: 0,
        },
      ],
      classGroups: [],
    })

    const { onSelectChange } = renderChangeMapPage()
    const user = userEvent.setup()

    await user.click(await screen.findByText('Rename greet to salute'))

    expect(onSelectChange).toHaveBeenCalledWith('test-change-key')
  })

  it("sets a Change's review state and shows the updated state", async () => {
    // Traces review_state_and_submission_frontend_rendering.feature:
    // "A reviewer sets a Change's review state from the Change Map"
    const change: ChangeMap['changes'][number] = {
      id: 0,
      changeKey: 'test-change-key',
      description: 'Rename greet to salute',
      category: 'BEHAVIORAL',
      kind: 'RENAME_SYMBOL',
      reviewState: 'UNSEEN',
      occurrenceCount: 1,
      exceptionCount: 0,
    }
    mockChangeMap({
      prTitle: 'Move authentication to Account',
      categoryCounts: { BEHAVIORAL: 1, STRUCTURAL: 0, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [change],
      classGroups: [],
    })
    let patchedState: string | null = null
    server.use(
      http.patch('/api/review/changes/test-change-key/review-state', async ({ request }) => {
        const body = (await request.json()) as { state: string }
        patchedState = body.state
        return new HttpResponse(null, { status: 200 })
      }),
    )

    renderChangeMapPage()
    const user = userEvent.setup()

    const select = await screen.findByLabelText('Review state for Rename greet to salute')
    mockChangeMap({
      prTitle: 'Move authentication to Account',
      categoryCounts: { BEHAVIORAL: 1, STRUCTURAL: 0, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [{ ...change, reviewState: 'REVIEWED' }],
      classGroups: [],
    })
    await user.selectOptions(select, 'Reviewed')

    expect(patchedState).toBe('REVIEWED')
    expect(await screen.findByText('Reviewed')).toBeVisible()
  })

  it('renders an all-zero summary and no Changes for an empty Change Map', async () => {
    // Given the reviewer has selected a PR titled "No-op PR" with no detected Changes
    mockChangeMap({
      prTitle: 'No-op PR',
      categoryCounts: { BEHAVIORAL: 0, STRUCTURAL: 0, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [],
      classGroups: [],
    })

    // When the reviewer views the Change Map page
    renderChangeMapPage()

    // Then the PR Understanding summary shows the title "No-op PR"
    expect(await screen.findByText('No-op PR')).toBeVisible()

    // And the summary shows 0 Behavioral, 0 Structural, 0 Mechanical, and 0 Unknown Changes
    expect(screen.getByText('Behavioral').nextElementSibling).toHaveTextContent('0')
    expect(screen.getByText('Structural').nextElementSibling).toHaveTextContent('0')
    expect(screen.getByText('Mechanical').nextElementSibling).toHaveTextContent('0')
    expect(screen.getByText('Unknown').nextElementSibling).toHaveTextContent('0')

    // And the Change Map lists no Changes
    expect(screen.getByText('No Changes detected.')).toBeVisible()
  })

  it('collapses multiple Changes on the same class into one expandable group', async () => {
    // Given a Change Map where two Structural Changes both belong to DeviceConfiguration
    const entryA = {
      id: 0,
      changeKey: 'key-a',
      description: 'Change signature of DeviceConfiguration#deviceApplicationService',
      category: 'STRUCTURAL' as const,
      kind: 'CHANGE_METHOD_SIGNATURE' as const,
      reviewState: 'UNSEEN' as const,
      occurrenceCount: 1,
      exceptionCount: 0,
    }
    const entryB = {
      ...entryA,
      id: 1,
      changeKey: 'key-b',
      description: 'Change signature of DeviceConfiguration#deviceUseCases',
    }
    mockChangeMap({
      prTitle: 'Crowdness live',
      categoryCounts: { BEHAVIORAL: 0, STRUCTURAL: 2, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [entryA, entryB],
      classGroups: [{ enclosingType: 'DeviceConfiguration', entries: [entryA, entryB] }],
    })

    // When the reviewer views the Change Map page
    renderChangeMapPage()

    // Then the two Changes are collapsed under one "DeviceConfiguration" group row
    // instead of appearing as two disconnected entries
    const groupRow = await screen.findByRole('button', { name: /DeviceConfiguration/ })
    expect(groupRow).toBeVisible()
    expect(screen.queryByText(/deviceApplicationService/)).not.toBeInTheDocument()

    // When the reviewer expands the group
    const user = userEvent.setup()
    await user.click(groupRow)

    // Then both underlying Changes become visible
    expect((await screen.findAllByText(/deviceApplicationService/)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/deviceUseCases/).length).toBeGreaterThan(0)
  })

  it('shows a "What changed and why" section when the PR spans multiple modules', async () => {
    // Given a PR whose Change Map spans two modules
    mockChangeMap({
      prTitle: 'Crowdness live',
      categoryCounts: { BEHAVIORAL: 0, STRUCTURAL: 1, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [],
      classGroups: [],
    })
    mockModules([
      { moduleName: 'crowdness-live', changeKeys: ['key-a'], narrative: null },
      { moduleName: 'crowdness-ingestion', changeKeys: ['key-b'], narrative: null },
    ])

    // When the reviewer views the Change Map page
    renderChangeMapPage()

    // Then a "What changed and why" section lists both modules
    expect(await screen.findByText('What changed and why')).toBeVisible()
    expect(screen.getByText('crowdness-live')).toBeVisible()
    expect(screen.getByText('crowdness-ingestion')).toBeVisible()
  })

  it('does not show the "What changed and why" section for a single-module PR', async () => {
    // Given a PR whose Change Map spans only one module
    mockChangeMap({
      prTitle: 'Move authentication to Account',
      categoryCounts: { BEHAVIORAL: 0, STRUCTURAL: 0, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [],
      classGroups: [],
    })
    mockModules([{ moduleName: 'athena', changeKeys: ['key-a'], narrative: null }])

    // When the reviewer views the Change Map page
    renderChangeMapPage()
    await screen.findByText('Move authentication to Account')

    // Then no "What changed and why" section is shown
    expect(screen.queryByText('What changed and why')).not.toBeInTheDocument()
  })

  it('lets the reviewer request and see a module narrative', async () => {
    // Given a PR spanning two modules, with an AI provider configured
    server.use(http.get('/api/ai/status', () => HttpResponse.json({ configured: true })))
    mockChangeMap({
      prTitle: 'Crowdness live',
      categoryCounts: { BEHAVIORAL: 0, STRUCTURAL: 1, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [],
      classGroups: [],
    })
    mockModules([
      { moduleName: 'crowdness-live', changeKeys: ['key-a'], narrative: null },
      { moduleName: 'crowdness-ingestion', changeKeys: ['key-b'], narrative: null },
    ])
    mockModuleNarrative('crowdness-live', {
      moduleName: 'crowdness-live',
      changeKeys: ['key-a'],
      narrative: 'Adds real-time occupancy tracking per zone.',
    })

    // When the reviewer views the Change Map page and asks for the narrative
    renderChangeMapPage()
    await screen.findByText('crowdness-live')
    const user = userEvent.setup()
    const explainButtons = await screen.findAllByRole('button', { name: 'Explain' })
    await user.click(explainButtons[0])

    // Then the narrative text appears
    expect(await screen.findByText('Adds real-time occupancy tracking per zone.')).toBeVisible()
  })

  it('clicking "Review summary" calls onOpenPreSubmissionSummary', async () => {
    mockChangeMap({
      prTitle: 'Move authentication to Account',
      categoryCounts: { BEHAVIORAL: 0, STRUCTURAL: 0, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [],
      classGroups: [],
    })

    const { onOpenPreSubmissionSummary } = renderChangeMapPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Review summary' }))

    expect(onOpenPreSubmissionSummary).toHaveBeenCalled()
  })

  it('clicking "AI analysis" calls onOpenAiAnalysis', async () => {
    mockChangeMap({
      prTitle: 'Move authentication to Account',
      categoryCounts: { BEHAVIORAL: 0, STRUCTURAL: 0, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [],
      classGroups: [],
    })

    const { onOpenAiAnalysis } = renderChangeMapPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'AI analysis' }))

    expect(onOpenAiAnalysis).toHaveBeenCalled()
  })

  it('routes back to the connect step when the reviewer is not connected to GitHub', async () => {
    // Given the reviewer is not connected to GitHub
    server.use(http.get('/api/review/change-map', () => new HttpResponse(null, { status: 401 })))

    // When the reviewer views the Change Map page
    const { onNotConnected, onNoPullRequestSelected } = renderChangeMapPage()

    // Then the reviewer is routed back to the connect step
    await vi.waitFor(() => expect(onNotConnected).toHaveBeenCalled())
    expect(onNoPullRequestSelected).not.toHaveBeenCalled()
  })

  it('routes back to PR selection when the reviewer has not selected a PR', async () => {
    // Given the reviewer is connected to GitHub but has not selected a PR
    server.use(http.get('/api/review/change-map', () => new HttpResponse(null, { status: 409 })))

    // When the reviewer views the Change Map page
    const { onNotConnected, onNoPullRequestSelected } = renderChangeMapPage()

    // Then the reviewer is routed back to PR selection
    await vi.waitFor(() => expect(onNoPullRequestSelected).toHaveBeenCalled())
    expect(onNotConnected).not.toHaveBeenCalled()
  })
})

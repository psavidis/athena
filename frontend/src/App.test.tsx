import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App'
import { server } from './test/server'

// Exercises the routing this ticket's follow-up (#122) changes on top of
// #73's flow and #91's Explorer: selecting a PR now lands directly on the
// Semantic Change Explorer (there is no Change Map screen to land on), and
// the Explorer's underlying 401/409 responses route back to earlier steps.

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
}

function mockChangeMapAndModules(prTitle = 'Move authentication to Account') {
  server.use(
    http.get('/api/review/change-map', () =>
      HttpResponse.json({ prTitle, categoryCounts: {}, changes: [], classGroups: [] }),
    ),
    http.get('/api/review/modules', () => HttpResponse.json([])),
  )
}

async function connectSelectRepoAndPr() {
  server.use(
    http.get('/api/github/status', () => HttpResponse.json({ connected: true, accountLogin: 'octocat' })),
    http.get('/api/repositories', () => HttpResponse.json([{ fullName: 'octocat/hello-world' }])),
    http.get('/api/repositories/octocat/hello-world/pulls', () =>
      HttpResponse.json([{ number: 1, title: 'Move authentication to Account' }]),
    ),
    http.post('/api/repositories/octocat/hello-world/pulls/1/select', () =>
      HttpResponse.json({
        number: 1,
        title: 'Move authentication to Account',
        author: 'octocat',
        baseRevision: 'abc123',
        headRevision: 'def456',
      }),
    ),
  )

  renderApp()
  const user = userEvent.setup()

  await user.click(await screen.findByRole('button', { name: 'octocat/hello-world' }))

  await user.click(await screen.findByRole('button', { name: /#1.*Move authentication to Account/ }))
}

describe('App routing', () => {
  it('renders the Semantic Change Explorer, aggregated across the whole PR, after a PR is selected', async () => {
    mockChangeMapAndModules()
    server.use(http.get('/api/review/semantic-profile', () => HttpResponse.json({ dimensions: [] })))

    await connectSelectRepoAndPr()

    expect(await screen.findByText('Move authentication to Account')).toBeVisible()
    expect(screen.getByRole('navigation', { name: 'Semantic levels' })).toBeVisible()
  })

  it('routes back to the connect step when the Explorer reports not connected', async () => {
    mockChangeMapAndModules()
    server.use(http.get('/api/review/semantic-profile', () => new HttpResponse(null, { status: 401 })))

    await connectSelectRepoAndPr()

    expect(await screen.findByText('Connect to GitHub')).toBeVisible()
  })

  it('routes back to PR selection when the Explorer reports no PR selected', async () => {
    mockChangeMapAndModules()
    server.use(http.get('/api/review/semantic-profile', () => new HttpResponse(null, { status: 409 })))

    await connectSelectRepoAndPr()

    expect(await screen.findByText('Open PRs — octocat/hello-world')).toBeVisible()
  })

  it('drills into a Change from the Explorer, then opens Diff view for it', async () => {
    server.use(
      http.get('/api/review/change-map', () =>
        HttpResponse.json({
          prTitle: 'Move authentication to Account',
          categoryCounts: {},
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
        }),
      ),
      http.get('/api/review/modules', () => HttpResponse.json([])),
      http.get('/api/review/semantic-profile', () =>
        HttpResponse.json({
          dimensions: [
            {
              dimension: 'STRUCTURAL',
              conceptName: 'Rename',
              conceptDescription: 'A symbol was renamed.',
              inferred: false,
              confidencePercent: 100,
              evidence: ['- greet()\n+ salute()'],
              supportingConceptNames: [],
              beforeEvidenceCount: 0,
            },
          ],
        }),
      ),
      http.get('/api/review/change-map/test-change-key/semantic-profile', () =>
        HttpResponse.json({
          dimensions: [
            {
              dimension: 'STRUCTURAL',
              conceptName: 'Rename',
              conceptDescription: 'A symbol was renamed.',
              inferred: false,
              confidencePercent: 100,
              evidence: ['- greet()\n+ salute()'],
              supportingConceptNames: [],
              beforeEvidenceCount: 0,
            },
          ],
        }),
      ),
      http.get('/api/review/changes/test-change-key', () =>
        HttpResponse.json({
          changeKey: 'test-change-key',
          category: 'BEHAVIORAL',
          kind: 'RENAME_SYMBOL',
          description: 'Rename greet to salute',
          symbols: ['Greeter#greet'],
          files: ['Greeter.java'],
          diff: '',
        }),
      ),
    )

    await connectSelectRepoAndPr()
    const user = userEvent.setup()

    // The Explorer starts scoped to the whole PR — no Diff view toggle yet.
    expect(screen.queryByText('Diff view')).not.toBeInTheDocument()

    // Drill into the one Change from the flat list in the evidence panel.
    await user.click(await screen.findByText(/Show all 1 Change/))
    await user.click(screen.getByText('Rename greet to salute'))

    // Now scoped to that Change, Diff view is reachable.
    await user.click(await screen.findByText('Diff view'))

    expect(await screen.findByText('Greeter#greet')).toBeVisible()
  })
})

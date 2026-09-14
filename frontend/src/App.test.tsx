import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App'
import { server } from './test/server'

// Selecting a PR now lands directly on the Semantic Canvas (ticket #129),
// replacing the retired Semantic Change Explorer shell — there is no Change
// Map screen to land on, and the Canvas's own 401/409 responses route back
// to earlier steps the same way the Explorer's did.

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
}

function mockTopology() {
  server.use(http.get('/api/review/topology', () => HttpResponse.json({ territories: [], dependencies: [] })))
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

  // The repo/PR picker (ticket #128 follow-up) lives as two chained
  // dropdowns in the top bar rather than standalone screens — open each and
  // choose the option it lists.
  await user.click(await screen.findByRole('button', { name: 'Select a repository…' }))
  await user.click(await screen.findByRole('option', { name: 'octocat/hello-world' }))

  await user.click(await screen.findByRole('button', { name: 'Select a PR…' }))
  await user.click(await screen.findByRole('option', { name: /#1.*Move authentication to Account/ }))
}

describe('App routing', () => {
  it('renders the Semantic Canvas after a PR is selected', async () => {
    mockTopology()

    await connectSelectRepoAndPr()

    expect(await screen.findByRole('application', { name: 'Semantic Canvas territory map' })).toBeVisible()
  })

  it('routes back to the not-connected prompt when the Canvas reports not connected', async () => {
    server.use(http.get('/api/review/topology', () => new HttpResponse(null, { status: 401 })))

    await connectSelectRepoAndPr()

    expect(await screen.findByText('Not connected to GitHub')).toBeVisible()
  })

  it('routes back to the empty canvas shell when the Canvas reports no PR selected', async () => {
    server.use(http.get('/api/review/topology', () => new HttpResponse(null, { status: 409 })))

    await connectSelectRepoAndPr()

    expect(
      await screen.findByText('Select a repository and Pull Request above to open the Semantic Canvas.'),
    ).toBeVisible()
  })
})

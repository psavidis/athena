import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App'
import { server } from './test/server'

// Exercises the routing this ticket adds on top of #73's flow: selecting a
// PR now lands on the Change Map instead of the old placeholder step, and
// the Change Map's 401/409 responses route back to earlier steps.

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
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
  it('renders the Change Map after a PR is selected', async () => {
    server.use(
      http.get('/api/review/change-map', () =>
        HttpResponse.json({
          prTitle: 'Move authentication to Account',
          categoryCounts: { BEHAVIORAL: 1, STRUCTURAL: 0, MECHANICAL: 0, UNKNOWN: 0 },
          changes: [],
        }),
      ),
    )

    await connectSelectRepoAndPr()

    expect(await screen.findByText('Behavioral')).toBeVisible()
  })

  it('routes back to the connect step when the Change Map reports not connected', async () => {
    server.use(http.get('/api/review/change-map', () => new HttpResponse(null, { status: 401 })))

    await connectSelectRepoAndPr()

    expect(await screen.findByText('Connect to GitHub')).toBeVisible()
  })

  it('routes back to PR selection when the Change Map reports no PR selected', async () => {
    server.use(http.get('/api/review/change-map', () => new HttpResponse(null, { status: 409 })))

    await connectSelectRepoAndPr()

    expect(await screen.findByText('Open PRs — octocat/hello-world')).toBeVisible()
  })

  it('opens a Change detail view from the Change Map and back again', async () => {
    server.use(
      http.get('/api/review/change-map', () =>
        HttpResponse.json({
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

    await user.click(await screen.findByText('Rename greet to salute'))

    expect(await screen.findByText('Greeter#greet')).toBeVisible()

    await user.click(screen.getByText('← Back to Change Map'))

    expect(await screen.findByRole('heading', { name: 'Move authentication to Account' })).toBeVisible()
  })
})

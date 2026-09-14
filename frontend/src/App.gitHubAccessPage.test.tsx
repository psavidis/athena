import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/github_access_page.feature

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
}

describe('GitHub Access page', () => {
  it("shows connection status, account, and accessible repositories for a connected user", async () => {
    // Given the user is connected to GitHub as "octocat" with access to "octocat/hello-world"
    server.use(
      http.get('/api/github/status', () =>
        HttpResponse.json({ connected: true, accountLogin: 'octocat', installationConfigureUrl: 'https://github.com/settings/installations/1' }),
      ),
      http.get('/api/repositories', () => HttpResponse.json([{ fullName: 'octocat/hello-world' }])),
    )
    renderApp()

    // When the user opens the GitHub Access page
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'GitHub Access' }))

    // Then the page shows the connection status as connected, the connected account, and the repository
    expect(await screen.findByText('Connected')).toBeVisible()
    expect(screen.getByText('Connected as octocat')).toBeVisible()
    expect(await screen.findByText('octocat/hello-world')).toBeVisible()
  })

  it('shows a way to connect for a not-yet-connected user', async () => {
    // Given the user is not connected to GitHub
    server.use(http.get('/api/github/status', () => HttpResponse.json({ connected: false, accountLogin: null, installationConfigureUrl: null })))
    renderApp()

    // When the user opens the GitHub Access page
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Go to GitHub Access' }))

    // Then the page shows not connected, with a way to connect
    expect(await screen.findByText('Not connected')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Connect GitHub' })).toBeVisible()
  })

  it("links to GitHub's own installation access settings", async () => {
    // Given the user is connected to GitHub as "octocat"
    server.use(
      http.get('/api/github/status', () =>
        HttpResponse.json({ connected: true, accountLogin: 'octocat', installationConfigureUrl: 'https://github.com/settings/installations/1' }),
      ),
      http.get('/api/repositories', () => HttpResponse.json([])),
    )
    renderApp()

    // When the user opens the GitHub Access page
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'GitHub Access' }))

    // Then the page shows a link to GitHub's installation access settings
    const link = await screen.findByRole('link', { name: /Configure access on GitHub/ })
    expect(link).toHaveAttribute('href', 'https://github.com/settings/installations/1')
  })

  it('shows a non-blocking prompt on the main page when not connected', async () => {
    // Given the user is not connected to GitHub
    server.use(http.get('/api/github/status', () => HttpResponse.json({ connected: false, accountLogin: null, installationConfigureUrl: null })))

    // When the user opens the main page
    renderApp()

    // Then the main page shows a prompt linking to the GitHub Access page, not a full-page takeover
    expect(await screen.findByText('Not connected to GitHub')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Go to GitHub Access' })).toBeVisible()
  })

  it('shows a repository-list error on the GitHub Access page without touching the main flow', async () => {
    // Given the user is connected to GitHub but the repository list request fails
    server.use(
      http.get('/api/github/status', () =>
        HttpResponse.json({ connected: true, accountLogin: 'octocat', installationConfigureUrl: null }),
      ),
      http.get('/api/repositories', () => new HttpResponse(null, { status: 500 })),
    )
    renderApp()

    // When the user opens the GitHub Access page
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'GitHub Access' }))

    // Then the page shows the error there
    expect(await screen.findByText('Could not load accessible repositories.')).toBeVisible()
  })

  it('lets the user navigate from the main page to the GitHub Access page and back', async () => {
    server.use(
      http.get('/api/github/status', () =>
        HttpResponse.json({ connected: true, accountLogin: 'octocat', installationConfigureUrl: null }),
      ),
      http.get('/api/repositories', () => HttpResponse.json([{ fullName: 'octocat/hello-world' }])),
    )
    renderApp()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'GitHub Access' }))
    expect(await screen.findByText('Connected')).toBeVisible()

    await user.click(screen.getByRole('button', { name: '← Back' }))

    expect(await screen.findByText('octocat/hello-world')).toBeVisible()
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/diff_entry_point.feature

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
}

describe('Diff entry point', () => {
  it('shows a way to start a Diff alongside the GitHub repository picker', async () => {
    // Given the user is connected to GitHub
    server.use(
      http.get('/api/github/status', () =>
        HttpResponse.json({ connected: true, accountLogin: 'octocat', installationConfigureUrl: null }),
      ),
      http.get('/api/repositories', () => HttpResponse.json([{ fullName: 'octocat/hello-world' }])),
    )

    // When the user opens the main page
    renderApp()

    // Then the main page shows a way to start a Diff and still shows the GitHub repository picker
    expect(await screen.findByRole('button', { name: /Start a Diff/ })).toBeVisible()
    expect(await screen.findByText('octocat/hello-world')).toBeVisible()
  })

  it('shows a way to start a Diff when the user is not connected to GitHub', async () => {
    // Given the user is not connected to GitHub
    server.use(http.get('/api/github/status', () => HttpResponse.json({ connected: false, accountLogin: null, installationConfigureUrl: null })))

    // When the user opens the main page
    renderApp()

    // Then the main page shows a way to start a Diff
    expect(await screen.findByRole('button', { name: /Start a Diff/ })).toBeVisible()
  })

  it('starting a Diff lands on the Semantic Canvas', async () => {
    server.use(http.get('/api/github/status', () => HttpResponse.json({ connected: false, accountLogin: null, installationConfigureUrl: null })))
    server.use(
      http.post('/api/diffs', () => HttpResponse.json({ changeCount: 1 })),
      http.get('/api/review/topology', () =>
        HttpResponse.json({
          territories: [
            {
              moduleName: 'some-module',
              status: 'TOUCHED',
              fileCount: 1,
              statusSummary: '1 file',
              techStack: 'JAVA',
              techStackLabel: 'Java',
              changeKeys: [],
            },
          ],
          dependencies: [],
        }),
      ),
    )
    renderApp()
    const user = userEvent.setup()

    // Given the user is on the Diff entry point
    await user.click(await screen.findByRole('button', { name: /Start a Diff/ }))

    // When the user starts a Diff from a local repository path and two revisions
    await user.type(await screen.findByRole('textbox', { name: /Repository path/ }), '/tmp/some-repo')
    await user.type(screen.getByRole('textbox', { name: /Base revision/ }), 'main')
    await user.type(screen.getByRole('textbox', { name: /Head revision/ }), 'feature-branch')
    await user.click(screen.getByRole('button', { name: 'Compare' }))

    // Then the user lands on the Semantic Canvas showing that Diff's analysis
    expect(await screen.findByRole('button', { name: 'some-module territory' })).toBeVisible()
  })

  it('shows an error on the form when the revision does not exist, without navigating away', async () => {
    server.use(http.get('/api/github/status', () => HttpResponse.json({ connected: false, accountLogin: null, installationConfigureUrl: null })))
    server.use(http.post('/api/diffs', () => new HttpResponse(null, { status: 400 })))
    renderApp()
    const user = userEvent.setup()

    // Given the user is on the Diff entry point
    await user.click(await screen.findByRole('button', { name: /Start a Diff/ }))

    // When the user starts a Diff with a revision that does not exist
    await user.type(await screen.findByRole('textbox', { name: /Repository path/ }), '/tmp/some-repo')
    await user.type(screen.getByRole('textbox', { name: /Base revision/ }), 'main')
    await user.type(screen.getByRole('textbox', { name: /Head revision/ }), 'does-not-exist')
    await user.click(screen.getByRole('button', { name: 'Compare' }))

    // Then the Diff entry point shows an error and the user remains on it
    expect(await screen.findByText(/Could not create Diff/)).toBeVisible()
    expect(screen.getByRole('textbox', { name: /Repository path/ })).toBeVisible()
  })
})

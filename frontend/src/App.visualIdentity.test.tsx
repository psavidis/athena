import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/visual_identity.feature

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
}

describe('Athena visual identity', () => {
  it('shows the Athena logo in the application header', async () => {
    // Given a user opens the Athena web application
    server.use(http.get('/api/github/status', () => HttpResponse.json({ connected: false })))
    renderApp()

    // Then the Athena logo is visible in the application's header
    expect(await screen.findByRole('img', { name: 'Athena' })).toBeVisible()
  })

  it('shows the unified loading indicator while the repository picker is loading', async () => {
    // Given a user triggers an action that loads data
    server.use(
      http.get('/api/github/status', () => HttpResponse.json({ connected: true, accountLogin: 'octocat' })),
      http.get(
        '/api/repositories',
        () => new Promise(() => {}), // never resolves: the loading state persists for the assertion
      ),
    )
    renderApp()
    const user = userEvent.setup()

    // When the user opens the repository picker before its data has loaded
    await user.click(await screen.findByRole('button', { name: 'Select a repository…' }))

    // Then the user sees Athena's unified loading indicator inside the picker
    expect(await screen.findByText('Loading…')).toBeVisible()
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
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

  it('shows the unified loading indicator while data is loading, on every page', async () => {
    // Given a user triggers an action that loads data
    server.use(
      http.get('/api/github/status', () => HttpResponse.json({ connected: true, accountLogin: 'octocat' })),
      http.get(
        '/api/repositories',
        () => new Promise(() => {}), // never resolves: the loading state persists for the assertion
      ),
    )
    renderApp()

    // When the data has not finished loading yet
    // Then the user sees Athena's unified loading indicator
    expect(await screen.findByRole('status', { name: 'Loading' })).toBeVisible()
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/knowledge_settings_page.feature

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
}

function mockNotConfigured() {
  server.use(
    http.get('/api/knowledge/status', () => HttpResponse.json({ configured: false, providerId: null, vaultPath: null })),
  )
}

describe('Knowledge (Obsidian) settings page', () => {
  it('shows a way to connect when no Knowledge Provider is configured', async () => {
    // Given the user is not connected to GitHub (the settings link is reachable either way)
    server.use(http.get('/api/github/status', () => HttpResponse.json({ connected: false, accountLogin: null, installationConfigureUrl: null })))
    mockNotConfigured()
    renderApp()

    // When the user opens the Knowledge settings page
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Knowledge settings' }))

    // Then the page shows the Knowledge Provider as not configured, with a way to configure a vault
    expect(await screen.findByText('Not configured')).toBeVisible()
    expect(screen.getByLabelText('Obsidian vault path')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Connect Obsidian' })).toBeVisible()
  })

  it('configures an Obsidian vault', async () => {
    server.use(http.get('/api/github/status', () => HttpResponse.json({ connected: false, accountLogin: null, installationConfigureUrl: null })))
    mockNotConfigured()
    renderApp()
    const user = userEvent.setup()

    // When the user opens the Knowledge settings page and submits a vault path
    await user.click(await screen.findByRole('button', { name: 'Knowledge settings' }))
    await user.type(await screen.findByLabelText('Obsidian vault path'), '/home/user/vault')

    server.use(
      http.post('/api/knowledge/obsidian', () =>
        HttpResponse.json({ configured: true, providerId: 'obsidian', vaultPath: '/home/user/vault' }),
      ),
    )
    await user.click(screen.getByRole('button', { name: 'Connect Obsidian' }))

    // Then the page shows the Knowledge Provider as configured, with the vault path
    expect(await screen.findByText('Configured')).toBeVisible()
    expect(screen.getByText('Obsidian vault: /home/user/vault')).toBeVisible()
  })

  it('disconnects a configured Knowledge Provider', async () => {
    server.use(http.get('/api/github/status', () => HttpResponse.json({ connected: false, accountLogin: null, installationConfigureUrl: null })))
    server.use(
      http.get('/api/knowledge/status', () =>
        HttpResponse.json({ configured: true, providerId: 'obsidian', vaultPath: '/home/user/vault' }),
      ),
    )
    renderApp()
    const user = userEvent.setup()

    // When the user opens the Knowledge settings page and disconnects
    await user.click(await screen.findByRole('button', { name: 'Knowledge settings' }))
    await screen.findByText('Configured')

    server.use(
      http.delete('/api/knowledge/obsidian', () =>
        HttpResponse.json({ configured: false, providerId: null, vaultPath: null }),
      ),
    )
    await user.click(screen.getByRole('button', { name: 'Disconnect' }))

    // Then the page shows the Knowledge Provider as not configured
    expect(await screen.findByText('Not configured')).toBeVisible()
  })

  it('links to the Knowledge settings page from the main page, alongside GitHub Access', async () => {
    // Given the user is connected to GitHub
    server.use(
      http.get('/api/github/status', () =>
        HttpResponse.json({ connected: true, accountLogin: 'octocat', installationConfigureUrl: null }),
      ),
      http.get('/api/repositories', () => HttpResponse.json([])),
    )
    renderApp()

    // When the user opens the main page
    // Then the main page shows a way to reach the Knowledge settings page, alongside GitHub Access
    expect(await screen.findByRole('button', { name: 'GitHub Access' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Knowledge' })).toBeVisible()
  })
})

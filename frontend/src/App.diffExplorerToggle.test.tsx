import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import App from './App'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_change_explorer_guided_review_and_mode_toggle.feature
// (the Diff view / Semantic Explorer toggle scenarios)

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
}

async function connectSelectPrAndChange() {
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
    http.get('/api/review/semantic-profile', () => HttpResponse.json({ dimensions: [] })),
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
    http.get('/api/review/change-map/test-change-key/semantic-profile', () => HttpResponse.json({ dimensions: [] })),
  )

  renderApp()
  const user = userEvent.setup()

  await user.click(await screen.findByRole('button', { name: 'octocat/hello-world' }))
  await user.click(await screen.findByRole('button', { name: /#1.*Move authentication to Account/ }))

  // Lands on the whole-PR Explorer — drill into the one Change via the flat
  // list in the evidence panel, then open Diff view for it, reaching this
  // helper's "viewing the diff view for a Change" starting state.
  await user.click(await screen.findByText(/Show all 1 Change/))
  await user.click(screen.getByText('Rename greet to salute'))
  await user.click(await screen.findByText('Diff view'))
  await screen.findByText('Greeter#greet')

  return user
}

describe('Diff view / Semantic Explorer toggle', () => {
  it('switches from the diff view to the Semantic Explorer for the same Change', async () => {
    // Given the reviewer is viewing the diff view for a Change
    const user = await connectSelectPrAndChange()
    expect(screen.getByText('Greeter#greet')).toBeVisible()

    // When the reviewer switches to the Semantic Explorer
    await user.click(screen.getByRole('button', { name: 'Semantic Explorer' }))

    // Then the reviewer sees the Semantic Change Explorer for that same Change
    expect(await screen.findByRole('navigation', { name: 'Semantic levels' })).toBeVisible()
  })

  it('switches from the Semantic Explorer back to the diff view for the same Change', async () => {
    // Given the reviewer is viewing the Semantic Change Explorer for a Change
    const user = await connectSelectPrAndChange()
    await user.click(screen.getByRole('button', { name: 'Semantic Explorer' }))
    await screen.findByRole('navigation', { name: 'Semantic levels' })

    // When the reviewer switches to the diff view
    await user.click(screen.getByRole('button', { name: 'Diff view' }))

    // Then the reviewer sees the diff view for that same Change
    expect(await screen.findByText('Greeter#greet')).toBeVisible()
  })

  it("does not lose the selected pull request's Change when switching modes", async () => {
    // Given the reviewer has selected a pull request and is viewing the diff view for one of its Changes
    const user = await connectSelectPrAndChange()

    // When the reviewer switches to the Semantic Explorer and back to the diff view
    await user.click(screen.getByRole('button', { name: 'Semantic Explorer' }))
    await screen.findByRole('navigation', { name: 'Semantic levels' })
    await user.click(screen.getByRole('button', { name: 'Diff view' }))

    // Then the reviewer is still viewing that same pull request's Change
    expect(await screen.findByText('Greeter#greet')).toBeVisible()
  })
})

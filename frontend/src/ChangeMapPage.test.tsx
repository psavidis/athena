import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import ChangeMapPage from './ChangeMapPage'
import type { ChangeMap } from './api'
import { server } from './test/server'

// Traces src/test/resources/features/change_map_frontend_rendering.feature

function renderChangeMapPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onNotConnected = vi.fn()
  const onNoPullRequestSelected = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <ChangeMapPage onNotConnected={onNotConnected} onNoPullRequestSelected={onNoPullRequestSelected} />
    </QueryClientProvider>,
  )
  return { onNotConnected, onNoPullRequestSelected }
}

function mockChangeMap(body: ChangeMap) {
  server.use(http.get('/api/review/change-map', () => HttpResponse.json(body)))
}

describe('Change Map & PR Understanding View rendering', () => {
  it('shows the PR Understanding summary with the PR title and a count per category', async () => {
    // Given the reviewer has selected a PR titled "Move authentication to Account"
    // And its Change Map has 2 Behavioral, 1 Structural, 3 Mechanical, and 0 Unknown Changes
    mockChangeMap({
      prTitle: 'Move authentication to Account',
      categoryCounts: { BEHAVIORAL: 2, STRUCTURAL: 1, MECHANICAL: 3, UNKNOWN: 0 },
      changes: [],
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
          description: 'Rename greet to salute',
          category: 'BEHAVIORAL',
          reviewState: 'UNSEEN',
          occurrenceCount: 1,
          exceptionCount: 0,
        },
      ],
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

  it('renders an all-zero summary and no Changes for an empty Change Map', async () => {
    // Given the reviewer has selected a PR titled "No-op PR" with no detected Changes
    mockChangeMap({
      prTitle: 'No-op PR',
      categoryCounts: { BEHAVIORAL: 0, STRUCTURAL: 0, MECHANICAL: 0, UNKNOWN: 0 },
      changes: [],
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

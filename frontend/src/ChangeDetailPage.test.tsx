import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import ChangeDetailPage from './ChangeDetailPage'
import type { ChangeDetail } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/change_drilldown_frontend_rendering.feature

function renderChangeDetailPage(changeKey = 'test-change-key') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onBack = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <ChangeDetailPage changeKey={changeKey} onBack={onBack} />
    </QueryClientProvider>,
  )
  return { onBack }
}

function mockChangeDetail(body: ChangeDetail) {
  server.use(http.get(`/api/review/changes/${body.changeKey}`, () => HttpResponse.json(body)))
}

const RENAME_DETAIL: ChangeDetail = {
  changeKey: 'test-change-key',
  category: 'BEHAVIORAL',
  kind: 'RENAME_SYMBOL',
  description: 'Rename greet to salute',
  symbols: ['Greeter#greet', 'Greeter#salute'],
  files: ['Greeter.java'],
  diff: '- greet()\n+ salute()',
  testCode: false,
}

describe('Change detail view rendering', () => {
  it('shows the description, category, symbols, files, and diff', async () => {
    // Given the reviewer is viewing the Change Map with a Change described as "Rename greet to salute"
    // When the reviewer clicks that Change
    mockChangeDetail(RENAME_DETAIL)
    renderChangeDetailPage()

    // Then the detail view shows the description "Rename greet to salute"
    expect(await screen.findByText('Rename greet to salute')).toBeVisible()
    // And the detail view shows the Change's category
    expect(screen.getByText('Behavioral')).toBeVisible()
    // And the detail view lists the involved symbols
    expect(screen.getByText('Greeter#greet')).toBeVisible()
    expect(screen.getByText('Greeter#salute')).toBeVisible()
    // And the detail view lists the touched files
    expect(screen.getByText('Greeter.java')).toBeVisible()
    // And the detail view shows the underlying diff
    expect(screen.getByText(/salute\(\)/)).toBeVisible()
  })

  it('visually distinguishes added and removed diff lines', async () => {
    // Given the reviewer is viewing a Change with a recorded diff
    mockChangeDetail(RENAME_DETAIL)
    renderChangeDetailPage()
    await screen.findByText('Rename greet to salute')

    // Then removed lines and added lines are styled differently from each other
    const removedMarker = screen.getByText('-')
    const addedMarker = screen.getByText('+')
    expect(removedMarker.style.color).not.toEqual(addedMarker.style.color)
    expect(removedMarker.parentElement?.style.backgroundColor).not.toEqual(addedMarker.parentElement?.style.backgroundColor)
  })

  it('shows a fallback message when no diff was recorded', async () => {
    // Given a Change with no recorded diff
    mockChangeDetail({ ...RENAME_DETAIL, diff: '' })
    renderChangeDetailPage()
    await screen.findByText('Rename greet to salute')

    // Then the diff panel explains none is available, instead of showing nothing
    expect(screen.getByText('No diff recorded for this Change.')).toBeVisible()
  })

  it('lets the reviewer attach a comment', async () => {
    // Given the reviewer is viewing a Change's detail view
    mockChangeDetail(RENAME_DETAIL)
    server.use(
      http.post('/api/review/comments', () => HttpResponse.json({ comments: ['Good refactor'], privateNotes: [] })),
    )
    renderChangeDetailPage()
    await screen.findByText('Rename greet to salute')
    const user = userEvent.setup()

    // When the reviewer submits the comment "Good refactor" at Change scope
    const commentInput = screen.getByLabelText('Add a comment')
    await user.type(commentInput, 'Good refactor')
    await user.click(commentInput.closest('form')!.querySelector('button')!)

    // Then the detail view's comments list shows "Good refactor"
    expect(await screen.findByText('Good refactor')).toBeVisible()
  })

  it('lets the reviewer attach a private note that is visually distinct from a comment', async () => {
    // Given the reviewer is viewing a Change's detail view
    mockChangeDetail(RENAME_DETAIL)
    server.use(
      http.post('/api/review/private-notes', () =>
        HttpResponse.json({ comments: [], privateNotes: ['Ask the author about this'] }),
      ),
    )
    renderChangeDetailPage()
    await screen.findByText('Rename greet to salute')
    const user = userEvent.setup()

    // When the reviewer submits the private note "Ask the author about this" at Change scope
    const noteInput = screen.getByLabelText('Add a private note')
    await user.type(noteInput, 'Ask the author about this')
    await user.click(noteInput.closest('form')!.querySelector('button')!)

    // Then the detail view's private notes list shows "Ask the author about this"
    const note = await screen.findByText('Ask the author about this')
    expect(note).toBeVisible()
    // And the private note is styled distinctly from a comment
    expect(note.className).toContain('amber')
  })

  it('blocks submitting a blank comment without a request to the server', async () => {
    // Given the reviewer is viewing a Change's detail view
    mockChangeDetail(RENAME_DETAIL)
    renderChangeDetailPage()
    await screen.findByText('Rename greet to salute')

    // When the reviewer attempts to submit a blank comment
    // Then the submission is blocked without a request to the server (the button stays disabled)
    expect(screen.getByLabelText('Add a comment').closest('form')?.querySelector('button')).toBeDisabled()
  })

  it('returns to the Semantic Change Explorer for the same Change', async () => {
    // Given the reviewer is viewing a Change's detail view
    mockChangeDetail(RENAME_DETAIL)
    const { onBack } = renderChangeDetailPage()
    await screen.findByText('Rename greet to salute')
    const user = userEvent.setup()

    // When the reviewer switches to the Semantic Explorer
    await user.click(screen.getByRole('button', { name: 'Semantic Explorer' }))

    // Then the reviewer returns to the Explorer for that same Change
    expect(onBack).toHaveBeenCalled()
  })
})

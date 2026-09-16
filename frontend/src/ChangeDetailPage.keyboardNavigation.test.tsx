import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import ChangeDetailPage from './ChangeDetailPage'
import type { ChangeDetail } from './api'
import { server } from './test/server'
import { pressShortcut } from './test/keyboardShortcuts'

// Traces the "next file" scenario of
// frontend/src/test/resources/features/ui_first_experience/keyboard_review_navigation.feature

function renderChangeDetailPage(changeKey = 'test-change-key') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ChangeDetailPage changeKey={changeKey} onBack={vi.fn()} />
    </QueryClientProvider>,
  )
}

function mockChangeDetail(body: ChangeDetail) {
  server.use(http.get(`/api/review/changes/${body.changeKey}`, () => HttpResponse.json(body)))
}

const TWO_FILE_DETAIL: ChangeDetail = {
  changeKey: 'test-change-key',
  category: 'STRUCTURAL',
  kind: 'MOVE_SYMBOL',
  description: 'Move validate to Account',
  symbols: ['Account#validate'],
  files: ['Account.java', 'AccountTest.java'],
  diff: '- validate()\n+ moved',
}

describe('Keyboard navigation of a Change detail view', () => {
  it('moves keyboard focus to the next listed file', async () => {
    mockChangeDetail(TWO_FILE_DETAIL)
    renderChangeDetailPage()

    const files = await screen.findAllByTestId('touched-file')
    files[0].focus()

    pressShortcut('nextItem', files[0])

    expect(document.activeElement).toBe(files[1])
  })
})

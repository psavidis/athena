import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import ContextRewindPage from './ContextRewindPage'
import type { ContextRewind } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/context_rewind_layers.feature

function renderContextRewindPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ContextRewindPage entityName="PaymentProcessor" onBack={vi.fn()} />
    </QueryClientProvider>,
  )
}

function mockContextRewind(body: ContextRewind) {
  server.use(http.get('/api/review/context-rewind/PaymentProcessor', () => HttpResponse.json(body)))
}

const BASE: ContextRewind = {
  entityName: 'PaymentProcessor',
  evolutionTimeline: [{ description: 'PaymentProcessor introduced', occurredAt: '2025-12-01T00:00:00Z' }],
  pullRequestReferences: [],
  aiNarrative: null,
  insufficientHistoryMessage: null,
  knowledgeFacts: [],
}

async function openOverview() {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Zoom in' }))
  return user
}

describe('Context Rewind — Context Layers', () => {
  it('organizes its context into five distinct layers', async () => {
    // Given the developer has Context Rewind open for "PaymentProcessor" at the Overview level
    mockContextRewind(BASE)
    renderContextRewindPage()
    await openOverview()

    // Then Context Rewind lists the "Current", "Evolution", "Discussions", "Decisions", and "Knowledge" layers
    expect(screen.getByRole('button', { name: 'Current' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Evolution' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Discussions' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Decisions' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Knowledge' })).toBeVisible()
  })

  it('reveals what Athena remembers when the Knowledge layer is expanded', async () => {
    // Given "PaymentProcessor" has a Knowledge Base fact "Owned by the payments team"
    mockContextRewind({ ...BASE, knowledgeFacts: ['Owned by the payments team'] })
    renderContextRewindPage()
    const user = await openOverview()

    // When the developer expands the "Knowledge" layer
    await user.click(screen.getByRole('button', { name: 'Knowledge' }))

    // Then it shows "Owned by the payments team"
    expect(screen.getByText('Owned by the payments team')).toBeVisible()
  })

  it('keeps more than one layer expanded at the same time', async () => {
    // Given the developer has expanded the "Knowledge" layer
    mockContextRewind({ ...BASE, knowledgeFacts: ['Owned by the payments team'] })
    renderContextRewindPage()
    const user = await openOverview()
    await user.click(screen.getByRole('button', { name: 'Knowledge' }))

    // When the developer also expands the "Evolution" layer
    await user.click(screen.getByRole('button', { name: 'Evolution' }))

    // Then both the "Knowledge" and "Evolution" layers remain expanded
    expect(screen.getByText('Owned by the payments team')).toBeVisible()
    expect(screen.getByText('PaymentProcessor introduced')).toBeVisible()
  })

  it('states plainly that nothing is recorded for a layer with no available content', async () => {
    mockContextRewind(BASE)
    renderContextRewindPage()
    const user = await openOverview()

    // When the developer expands the "Discussions" layer
    await user.click(screen.getByRole('button', { name: 'Discussions' }))

    // Then it states that nothing is recorded for that layer
    expect(screen.getByText('Nothing recorded yet.')).toBeVisible()
  })
})

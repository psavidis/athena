import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology } from './api'
import { server } from './test/server'

// Traces the action-menu/tooltip scenarios of
// frontend/src/test/resources/features/ui_first_experience/keyboard_shortcut_discoverability.feature

function mockTopology(topology: ModuleTopology) {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(topology)))
}

function renderCanvas() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticCanvasPage pullRequest={null} onNotConnected={vi.fn()} onNoPullRequestSelected={vi.fn()} />
    </QueryClientProvider>,
  )
}

const ONE_TERRITORY: ModuleTopology = {
  territories: [
    {
      moduleName: 'crowdness-live',
      status: 'TOUCHED',
      fileCount: 5,
      statusSummary: '5 files',
      techStack: 'SPRING_BOOT_JAVA',
      techStackLabel: 'Spring Boot · Java',
      changeKeys: [],
    },
  ],
  dependencies: [],
}

describe('Contextual keyboard shortcut hints', () => {
  it('the "Zoom in" control exposes its keyboard shortcut as a tooltip', async () => {
    mockTopology(ONE_TERRITORY)
    renderCanvas()

    const zoomIn = await screen.findByRole('button', { name: 'Zoom in' })

    expect(zoomIn).toHaveAttribute('title', expect.stringContaining('+'))
  })
})

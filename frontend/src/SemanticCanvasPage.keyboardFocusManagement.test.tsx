import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'
import { pressShortcut } from './test/keyboardShortcuts'

// Traces frontend/src/test/resources/features/ui_first_experience/keyboard_focus_management.feature
//
// The comment-editor-opens-focus, dive-in-preserves-focus, and
// leave-preserves-focus scenarios are already covered by
// SemanticCanvasPage.keyboardCommenting.test.tsx and
// SemanticCanvasPage.keyboardNavigation.test.tsx respectively — this file
// covers the two context-change scenarios not exercised there: focus
// returning to the review context on comment submit/cancel.

function mockTopology(topology: ModuleTopology) {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(topology)))
}

function mockModuleProfile(moduleName: string, profile: SemanticProfile) {
  server.use(http.get(`/api/review/modules/${moduleName}/semantic-profile`, () => HttpResponse.json(profile)))
}

function mockCanvasComments() {
  server.use(
    http.get('/api/review/canvas-items/:itemId/comments', () => HttpResponse.json([])),
    http.get('/api/review/canvas-items/comment-counts', () => HttpResponse.json({})),
    http.post('/api/review/canvas-items/:itemId/comments', async ({ request }) => {
      const body = (await request.json()) as { text: string }
      return HttpResponse.json([{ id: 'c1', author: 'alex', postedAt: new Date().toISOString(), text: body.text }])
    }),
  )
}

function renderCanvas() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticCanvasPage pullRequest={null} onNotConnected={vi.fn()} onNoPullRequestSelected={vi.fn()} />
    </QueryClientProvider>,
  )
}

const LIVE_TERRITORY: ModuleTopology = {
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

const STRUCTURE_PROFILE: SemanticProfile = {
  dimensions: [
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Add validation',
      conceptDescription: 'Description',
      inferred: false,
      confidencePercent: 100,
      evidence: [],
      supportingConceptNames: [],
      filesTouched: ['PaymentValidator.java'],
    },
  ],
}

async function openCommentEditorOnFileNode() {
  const user = userEvent.setup()
  const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
  await user.click(territory)
  const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
  await user.click(within(rail).getByRole('button', { name: /Structure/ }))
  const fileNode = await screen.findByTestId('file-node')
  await user.click(fileNode)
  let drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
  await user.click(within(drawer).getByRole('button', { name: /Comment/ }))
  drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
  return { user, drawer, fileNode }
}

describe('Focus management across context changes', () => {
  it('submitting a comment returns keyboard focus to the item it was created on', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    mockCanvasComments()
    renderCanvas()
    const { user, drawer, fileNode } = await openCommentEditorOnFileNode()
    const editor = within(drawer).getByRole('textbox', { name: 'New comment' })
    await user.type(editor, 'Looks good')

    pressShortcut('submitComment', editor)
    // Wait for the real post-submit state (a rendered comment-entry), not the
    // still-open editor's own draft text — jsdom mirrors a <textarea>'s live
    // value into its text content, which would otherwise match immediately
    // regardless of whether submission actually happened.
    await within(drawer).findByTestId('comment-entry')

    expect(document.activeElement).toBe(fileNode)
  })

  it('cancelling a comment returns keyboard focus to the item it was opened from', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    mockCanvasComments()
    renderCanvas()
    const { drawer, fileNode } = await openCommentEditorOnFileNode()
    const editor = within(drawer).getByRole('textbox', { name: 'New comment' })

    pressShortcut('cancelComment', editor)

    expect(document.activeElement).toBe(fileNode)
  })
})

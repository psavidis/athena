import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'
import { pressShortcut } from './test/keyboardShortcuts'

// Traces frontend/src/test/resources/features/ui_first_experience/keyboard_commenting.feature
//
// Reply and resolve/unresolve don't exist anywhere in this codebase yet
// (neither via mouse nor keyboard) — ticket #159 introduces both, so this
// file's fake server also introduces the two new endpoints they need,
// mirrored on the existing add/edit/delete canvas-comment endpoints.

function mockTopology(topology: ModuleTopology) {
  server.use(http.get('/api/review/topology', () => HttpResponse.json(topology)))
}

function mockModuleProfile(moduleName: string, profile: SemanticProfile) {
  server.use(http.get(`/api/review/modules/${moduleName}/semantic-profile`, () => HttpResponse.json(profile)))
}

interface FakeComment {
  id: string
  author: string
  postedAt: string
  text: string
  resolved: boolean
  replies: { id: string; author: string; text: string }[]
}

function mockCanvasComments() {
  const byItem = new Map<string, FakeComment[]>()
  let nextId = 1

  server.use(
    http.get('/api/review/canvas-items/:itemId/comments', ({ params }) => {
      const itemId = decodeURIComponent(params.itemId as string)
      return HttpResponse.json(byItem.get(itemId) ?? [])
    }),
    http.get('/api/review/canvas-items/comment-counts', () => {
      const counts: Record<string, number> = {}
      for (const [itemId, comments] of byItem) {
        if (comments.length > 0) {
          counts[itemId] = comments.length
        }
      }
      return HttpResponse.json(counts)
    }),
    http.post('/api/review/canvas-items/:itemId/comments', async ({ params, request }) => {
      const itemId = decodeURIComponent(params.itemId as string)
      const body = (await request.json()) as { text: string }
      const comments = byItem.get(itemId) ?? []
      comments.push({ id: `c${nextId++}`, author: 'alex', postedAt: new Date().toISOString(), text: body.text, resolved: false, replies: [] })
      byItem.set(itemId, comments)
      return HttpResponse.json(comments)
    }),
    http.put('/api/review/canvas-items/:itemId/comments/:commentId', async ({ params, request }) => {
      const itemId = decodeURIComponent(params.itemId as string)
      const body = (await request.json()) as { text: string }
      const comments = byItem.get(itemId) ?? []
      const comment = comments.find((c) => c.id === (params.commentId as string))
      if (comment) comment.text = body.text
      return HttpResponse.json(comments)
    }),
    http.put('/api/review/canvas-items/:itemId/comments/:commentId/resolve', ({ params }) => {
      const itemId = decodeURIComponent(params.itemId as string)
      const comments = byItem.get(itemId) ?? []
      const comment = comments.find((c) => c.id === (params.commentId as string))
      if (comment) comment.resolved = !comment.resolved
      return HttpResponse.json(comments)
    }),
    http.post('/api/review/canvas-items/:itemId/comments/:commentId/replies', async ({ params, request }) => {
      const itemId = decodeURIComponent(params.itemId as string)
      const body = (await request.json()) as { text: string }
      const comments = byItem.get(itemId) ?? []
      const comment = comments.find((c) => c.id === (params.commentId as string))
      if (comment) comment.replies.push({ id: `r${nextId++}`, author: 'alex', text: body.text })
      return HttpResponse.json(comments)
    }),
  )
  return {
    seed(itemId: string, comments: FakeComment[]) {
      byItem.set(itemId, [...comments])
    },
  }
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

async function diveInAndOpenPaymentValidatorComments() {
  const user = userEvent.setup()
  const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
  await user.click(territory)
  const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
  await user.click(within(rail).getByRole('button', { name: /Structure/ }))
  await user.click(await screen.findByText('PaymentValidator.java'))
  let drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
  await user.click(within(drawer).getByRole('button', { name: /Comment/ }))
  drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
  await within(drawer).findByRole('textbox', { name: 'New comment' })
  return { user, drawer }
}

describe('Keyboard commenting', () => {
  it('creates a comment editor with the keyboard, without clicking any affordance', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    mockCanvasComments()
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))
    const fileNode = await screen.findByTestId('file-node')
    fileNode.focus()

    pressShortcut('createComment')

    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    const editor = await within(drawer).findByRole('textbox', { name: 'New comment' })
    expect(document.activeElement).toBe(editor)
  })

  it('submits a comment with the keyboard', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    mockCanvasComments()
    renderCanvas()
    const { user, drawer } = await diveInAndOpenPaymentValidatorComments()
    const editor = within(drawer).getByRole('textbox', { name: 'New comment' })
    await user.type(editor, 'Looks good')

    pressShortcut('submitComment', editor)

    // A jsdom quirk mirrors a <textarea>'s live value into its own text
    // content, so `getByText` alone could false-positive on the still-open,
    // unsubmitted editor — assert on a distinct posted comment-entry instead,
    // and that the draft itself was cleared (matching the existing
    // mouse-driven post flow, which clears the draft but leaves the editor
    // open for another comment rather than closing it).
    await within(drawer).findByTestId('comment-entry')
    expect(within(drawer).getByTestId('comment-entry')).toHaveTextContent('Looks good')
    expect(within(drawer).getByRole('textbox', { name: 'New comment' })).toHaveValue('')
  })

  it('cancels comment creation with the keyboard, discarding the draft', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    mockCanvasComments()
    renderCanvas()
    const { user, drawer } = await diveInAndOpenPaymentValidatorComments()
    const editor = within(drawer).getByRole('textbox', { name: 'New comment' })
    await user.type(editor, 'Draft thought')

    pressShortcut('cancelComment', editor)

    expect(within(drawer).queryByRole('textbox', { name: 'New comment' })).not.toBeInTheDocument()
  })

  it('replies to a comment with the keyboard', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    const fake = mockCanvasComments()
    fake.seed('file:crowdness-live:PaymentValidator.java', [
      { id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Why return null here?', resolved: false, replies: [] },
    ])
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))
    await user.click(await screen.findByTestId('comment-pin'))
    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    const commentEntry = await within(drawer).findByTestId('comment-entry')
    commentEntry.focus()

    pressShortcut('replyToComment', commentEntry)
    const replyField = within(drawer).getByRole('textbox', { name: 'Reply to comment' })
    await user.type(replyField, 'Legacy API contract')
    pressShortcut('submitComment', replyField)

    // {selector: 'p'} avoids Testing Library's own text-matching also
    // considering a <textarea>'s live value a match (the same jsdom/RTL
    // quirk noted elsewhere in this file) — here the reply textarea is
    // still mid-unmount, so an unscoped findByText can transiently resolve
    // to it instead of the rendered reply.
    expect(await within(commentEntry).findByText('Legacy API contract', { selector: 'p' })).toBeVisible()
  })

  it('edits a comment with the keyboard', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    const fake = mockCanvasComments()
    fake.seed('file:crowdness-live:PaymentValidator.java', [
      { id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Looks good overall', resolved: false, replies: [] },
    ])
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))
    await user.click(await screen.findByTestId('comment-pin'))
    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    const commentEntry = within(drawer).getByTestId('comment-entry')
    commentEntry.focus()

    pressShortcut('editComment', commentEntry)
    const editField = within(drawer).getByRole('textbox', { name: 'Edit comment' })
    await user.clear(editField)
    await user.type(editField, 'Looks great overall')
    pressShortcut('submitComment', editField)

    expect(await within(drawer).findByText('Looks great overall')).toBeVisible()
  })

  it('resolves a comment with the keyboard', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    const fake = mockCanvasComments()
    fake.seed('file:crowdness-live:PaymentValidator.java', [
      { id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Unresolved comment', resolved: false, replies: [] },
    ])
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))
    await user.click(await screen.findByTestId('comment-pin'))
    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    const commentEntry = within(drawer).getByTestId('comment-entry')
    commentEntry.focus()

    pressShortcut('resolveComment', commentEntry)

    expect(await within(commentEntry).findByTestId('comment-status')).toHaveTextContent('Resolved')
  })

  it('unresolves a previously resolved comment with the keyboard', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    const fake = mockCanvasComments()
    fake.seed('file:crowdness-live:PaymentValidator.java', [
      { id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Resolved comment', resolved: true, replies: [] },
    ])
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))
    await user.click(await screen.findByTestId('comment-pin'))
    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    const commentEntry = within(drawer).getByTestId('comment-entry')
    commentEntry.focus()

    pressShortcut('resolveComment', commentEntry)

    expect(await within(commentEntry).findByTestId('comment-status')).toHaveTextContent('Unresolved')
  })

  it('moves keyboard focus to the next comment', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    const fake = mockCanvasComments()
    fake.seed('file:crowdness-live:PaymentValidator.java', [
      { id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'First', resolved: false, replies: [] },
      { id: 'c2', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Second', resolved: false, replies: [] },
    ])
    renderCanvas()
    const user = userEvent.setup()
    const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
    await user.click(territory)
    const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
    await user.click(within(rail).getByRole('button', { name: /Structure/ }))
    await user.click(await screen.findByTestId('comment-pin'))
    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    const entries = within(drawer).getAllByTestId('comment-entry')
    entries[0].focus()

    pressShortcut('nextItem', entries[0])

    expect(document.activeElement).toBe(entries[1])
  })

  it('typing inside an open comment editor is not intercepted as a shortcut', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    mockCanvasComments()
    renderCanvas()
    const { user, drawer } = await diveInAndOpenPaymentValidatorComments()
    const editor = within(drawer).getByRole('textbox', { name: 'New comment' })

    // 'j' is this test suite's placeholder "next item" shortcut elsewhere in the review.
    await user.type(editor, 'jump to the next section')

    expect(editor).toHaveValue('jump to the next section')
    expect(await screen.findByRole('dialog', { name: 'Detail drawer' })).toBeVisible()
  })
})

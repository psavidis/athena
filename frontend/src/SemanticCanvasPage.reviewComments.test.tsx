import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticCanvasPage from './SemanticCanvasPage'
import type { ModuleTopology, SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_canvas_review_comments.feature

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
}

/**
 * A minimal in-memory fake of the canvas-comment endpoints (add/edit/delete/
 * list/counts) — the review-comments scenarios are inherently multi-step
 * (post, then verify the pin count; edit, then verify no duplicate), so a
 * static canned response per endpoint can't express them. Mirrors exactly
 * what CanvasCommentController does, at the HTTP boundary this component
 * actually talks to.
 */
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
      if (!body.text || body.text.trim() === '') {
        return new HttpResponse(null, { status: 400 })
      }
      const comments = byItem.get(itemId) ?? []
      comments.push({ id: `c${nextId++}`, author: 'alex', postedAt: new Date().toISOString(), text: body.text })
      byItem.set(itemId, comments)
      return HttpResponse.json(comments)
    }),
    http.put('/api/review/canvas-items/:itemId/comments/:commentId', async ({ params, request }) => {
      const itemId = decodeURIComponent(params.itemId as string)
      const commentId = params.commentId as string
      const body = (await request.json()) as { text: string }
      const comments = byItem.get(itemId) ?? []
      const comment = comments.find((c) => c.id === commentId)
      if (comment) {
        comment.text = body.text
      }
      return HttpResponse.json(comments)
    }),
    http.delete('/api/review/canvas-items/:itemId/comments/:commentId', ({ params }) => {
      const itemId = decodeURIComponent(params.itemId as string)
      const commentId = params.commentId as string
      const remaining = (byItem.get(itemId) ?? []).filter((c) => c.id !== commentId)
      byItem.set(itemId, remaining)
      return HttpResponse.json(remaining)
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

async function diveIntoLiveAndReachStructure() {
  const user = userEvent.setup()
  const territory = await screen.findByRole('button', { name: 'crowdness-live territory' })
  await user.click(territory)
  const rail = await screen.findByRole('navigation', { name: 'Zoom altitude · semantic spine' })
  await user.click(within(rail).getByRole('button', { name: /Structure/ }))
  return user
}

describe('Semantic Canvas — review comments', () => {
  it('shows a comment pin badge on a file node with comments', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    const fake = mockCanvasComments()
    fake.seed('file:crowdness-live:PaymentValidator.java', [
      { id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'First' },
      { id: 'c2', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Second' },
    ])
    renderCanvas()
    await diveIntoLiveAndReachStructure()

    const pin = await screen.findByTestId('comment-pin')
    expect(pin).toHaveTextContent('2')
  })

  it('shows no comment pin badge on a file node with no comments', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    mockCanvasComments()
    renderCanvas()
    await diveIntoLiveAndReachStructure()

    await screen.findByText('PaymentValidator.java')
    expect(screen.queryByTestId('comment-pin')).not.toBeInTheDocument()
  })

  it('opens the comment thread with author and timestamp when the pin is clicked', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    const fake = mockCanvasComments()
    fake.seed('file:crowdness-live:PaymentValidator.java', [
      { id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Should this handle the null case?' },
    ])
    renderCanvas()
    const user = await diveIntoLiveAndReachStructure()

    await user.click(await screen.findByTestId('comment-pin'))

    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    expect(within(drawer).getByText('Should this handle the null case?')).toBeVisible()
    expect(within(drawer).getByText('alex')).toBeVisible()
  })

  it('posting a new comment adds it to the thread and shows a pin badge', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    mockCanvasComments()
    renderCanvas()
    const user = await diveIntoLiveAndReachStructure()
    await user.click(await screen.findByText('PaymentValidator.java'))
    let drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    await user.click(within(drawer).getByRole('button', { name: /Comment/ }))
    drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })
    expect(within(drawer).getByText(/No comments yet/)).toBeVisible()

    await user.type(within(drawer).getByRole('textbox', { name: 'New comment' }), 'Should this handle the null case?')
    await user.click(within(drawer).getByRole('button', { name: 'Post comment' }))

    expect(await within(drawer).findByText('Should this handle the null case?')).toBeVisible()
    expect(await screen.findByTestId('comment-pin')).toHaveTextContent('1')
  })

  it('editing a comment replaces its text without creating a duplicate', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    const fake = mockCanvasComments()
    fake.seed('file:crowdness-live:PaymentValidator.java', [
      { id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Should this handle the null case?' },
    ])
    renderCanvas()
    const user = await diveIntoLiveAndReachStructure()
    await user.click(await screen.findByTestId('comment-pin'))
    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })

    await user.click(within(drawer).getByRole('button', { name: 'Edit' }))
    const editField = within(drawer).getByRole('textbox', { name: 'Edit comment' })
    await user.clear(editField)
    await user.type(editField, "Never mind, it's covered.")
    await user.click(within(drawer).getByRole('button', { name: 'Save' }))

    expect(await within(drawer).findByText("Never mind, it's covered.")).toBeVisible()
    expect(within(drawer).queryByText('Should this handle the null case?')).not.toBeInTheDocument()
    expect(within(drawer).getAllByTestId('comment-entry')).toHaveLength(1)
  })

  it('deleting a comment removes it and removes the pin badge', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    const fake = mockCanvasComments()
    fake.seed('file:crowdness-live:PaymentValidator.java', [
      { id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Only comment' },
    ])
    renderCanvas()
    const user = await diveIntoLiveAndReachStructure()
    await user.click(await screen.findByTestId('comment-pin'))
    const drawer = await screen.findByRole('dialog', { name: 'Detail drawer' })

    await user.click(within(drawer).getByRole('button', { name: 'Delete' }))

    expect(within(drawer).queryByText('Only comment')).not.toBeInTheDocument()
    expect(screen.queryByTestId('comment-pin')).not.toBeInTheDocument()
  })

  it('shows the total comment count across the PR in the topbar', async () => {
    mockTopology(LIVE_TERRITORY)
    mockModuleProfile('crowdness-live', STRUCTURE_PROFILE)
    const fake = mockCanvasComments()
    fake.seed('territory:crowdness-live', [
      { id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'One' },
      { id: 'c2', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Two' },
    ])
    fake.seed('file:crowdness-live:PaymentValidator.java', [
      { id: 'c3', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Three' },
    ])
    renderCanvas()

    expect(await screen.findByRole('button', { name: '3 comments' })).toBeVisible()
  })

  it('dims uncommented items when "show only commented" is toggled on, without removing them', async () => {
    mockTopology({
      territories: [
        LIVE_TERRITORY.territories[0],
        {
          moduleName: 'crowdness-ingestion',
          status: 'TOUCHED',
          fileCount: 3,
          statusSummary: '3 files',
          techStack: 'SPRING_BOOT_JAVA',
          techStackLabel: 'Spring Boot · Java',
          changeKeys: [],
        },
      ],
      dependencies: [],
    })
    const fake = mockCanvasComments()
    fake.seed('territory:crowdness-live', [{ id: 'c1', author: 'alex', postedAt: '2024-01-01T00:00:00Z', text: 'Comment' }])
    renderCanvas()
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'crowdness-live territory' })

    await user.click(await screen.findByRole('button', { name: /comments?$/ }))

    const commentedTerritory = screen.getByRole('button', { name: 'crowdness-live territory' })
    const uncommentedTerritory = screen.getByRole('button', { name: 'crowdness-ingestion territory' })
    expect(uncommentedTerritory).toBeVisible()
    expect(uncommentedTerritory.parentElement).toHaveClass('opacity-35')
    expect(commentedTerritory.parentElement).not.toHaveClass('opacity-35')
  })

  it('restores normal emphasis when "show only commented" is toggled back off', async () => {
    mockTopology(LIVE_TERRITORY)
    mockCanvasComments()
    renderCanvas()
    const user = userEvent.setup()
    const toggle = await screen.findByRole('button', { name: /comments?$/ })
    await user.click(toggle)
    const territory = screen.getByRole('button', { name: 'crowdness-live territory' })
    expect(territory.parentElement).toHaveClass('opacity-35')

    await user.click(toggle)

    expect(territory.parentElement).not.toHaveClass('opacity-35')
  })
})

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import ReviewReplayTimeline from './ReviewReplayTimeline'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/review_replay_timeline.feature

const QUESTION = {
  momentId: 'm1',
  kind: 'QUESTION',
  reference: 'entity:OrderService',
  taggedAt: '2026-09-17T10:00:00Z',
  status: 'CONFIRMED',
}

const DECISION = {
  momentId: 'm2',
  kind: 'DECISION',
  reference: 'entity:OrderService',
  taggedAt: '2026-09-17T10:05:00Z',
  status: 'CONFIRMED',
}

const DECISION_OTHER_ENTITY = { ...DECISION, reference: 'entity:PaymentService' }

function mockArtifact(moments: unknown[]) {
  server.use(
    http.get('/api/review-recordings/artifacts/recording-1', () => HttpResponse.json({ moments })),
  )
}

describe('Review Replay semantic timeline', () => {
  it('a developer sees the Replay\'s moments in chronological order', async () => {
    mockArtifact([QUESTION, DECISION])
    render(<ReviewReplayTimeline recordingId="recording-1" />)

    const buttons = await screen.findAllByRole('button', { name: /Question|Decision/ })

    expect(buttons.map((b) => b.textContent)).toEqual(['Question', 'Decision'])
  })

  it('moments referencing the same entity are shown grouped', async () => {
    mockArtifact([QUESTION, DECISION])
    render(<ReviewReplayTimeline recordingId="recording-1" />)

    const group = await screen.findByRole('group', { name: 'entity:OrderService' })

    expect(group).toHaveTextContent('Question')
    expect(group).toHaveTextContent('Decision')
  })

  it('moments referencing different entities are not grouped together', async () => {
    mockArtifact([QUESTION, DECISION_OTHER_ENTITY])
    render(<ReviewReplayTimeline recordingId="recording-1" />)

    const orderServiceGroup = await screen.findByRole('group', { name: 'entity:OrderService' })
    const paymentServiceGroup = await screen.findByRole('group', { name: 'entity:PaymentService' })

    expect(orderServiceGroup).toHaveTextContent('Question')
    expect(orderServiceGroup).not.toHaveTextContent('Decision')
    expect(paymentServiceGroup).toHaveTextContent('Decision')
  })

  it('a developer steps to the next moment', async () => {
    mockArtifact([QUESTION, DECISION])
    render(<ReviewReplayTimeline recordingId="recording-1" />)
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Question' })

    await user.click(screen.getByRole('button', { name: 'Next' }))

    expect(screen.getByRole('button', { name: 'Decision' })).toHaveAttribute('aria-current', 'true')
  })

  it('a developer steps to the previous moment', async () => {
    mockArtifact([QUESTION, DECISION])
    render(<ReviewReplayTimeline recordingId="recording-1" />)
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Question' })
    await user.click(screen.getByRole('button', { name: 'Next' }))

    await user.click(screen.getByRole('button', { name: 'Previous' }))

    expect(screen.getByRole('button', { name: 'Question' })).toHaveAttribute('aria-current', 'true')
  })

  it('stepping past the last moment has no effect', async () => {
    mockArtifact([QUESTION, DECISION])
    render(<ReviewReplayTimeline recordingId="recording-1" />)
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Question' })
    await user.click(screen.getByRole('button', { name: 'Next' }))

    await user.click(screen.getByRole('button', { name: 'Next' }))

    expect(screen.getByRole('button', { name: 'Decision' })).toHaveAttribute('aria-current', 'true')
  })

  it('stepping before the first moment has no effect', async () => {
    mockArtifact([QUESTION, DECISION])
    render(<ReviewReplayTimeline recordingId="recording-1" />)
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Question' })

    await user.click(screen.getByRole('button', { name: 'Previous' }))

    expect(screen.getByRole('button', { name: 'Question' })).toHaveAttribute('aria-current', 'true')
  })

  it('a developer jumps directly to a moment', async () => {
    mockArtifact([QUESTION, DECISION])
    render(<ReviewReplayTimeline recordingId="recording-1" />)
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Question' })

    await user.click(screen.getByRole('button', { name: 'Decision' }))

    expect(screen.getByRole('button', { name: 'Decision' })).toHaveAttribute('aria-current', 'true')
  })

  it('a Replay with no moments shows an empty timeline', async () => {
    mockArtifact([])
    render(<ReviewReplayTimeline recordingId="recording-1" />)

    expect(await screen.findByText('This Replay has no recorded moments.')).toBeVisible()
  })
})

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import ReviewRecordingControl from './ReviewRecordingControl'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/review_recording_control.feature

const DISCLOSURE = 'Starting a Review Recording captures navigation, code discussed, and questions/decisions.'

const SNAPSHOT = {
  recordingId: 'recording-1',
  repositoryFullName: 'acme/widgets',
  pullRequestNumber: 42,
  active: true,
  elapsedSeconds: 0,
  participantCount: 1,
  participantDisplayNames: ['Petros'],
}

function mockEndpoints() {
  server.use(
    http.get('/api/review-recordings/capture-disclosure', () => HttpResponse.json(DISCLOSURE)),
    http.post('/api/review-recordings', () =>
      HttpResponse.json({ recordingId: 'recording-1', snapshot: SNAPSHOT }),
    ),
    http.post('/api/review-recordings/recording-1/stop', () =>
      HttpResponse.json({ ...SNAPSHOT, active: false }),
    ),
  )
}

describe('Review Recording control', () => {
  it('a developer sees the disclosure before starting a recording', async () => {
    mockEndpoints()
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))

    expect(await screen.findByText(DISCLOSURE)).toBeVisible()
  })

  it('acknowledging the disclosure starts the recording and shows the indicator', async () => {
    mockEndpoints()
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))
    await user.click(await screen.findByRole('button', { name: 'Start recording' }))

    const indicator = await screen.findByRole('status', { name: 'Review Recording in progress' })
    expect(indicator).toHaveTextContent('Recording')
    expect(indicator).toHaveTextContent('0:00')
    expect(indicator).toHaveTextContent('1')
  })

  it('a developer stops an active recording', async () => {
    mockEndpoints()
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))
    await user.click(await screen.findByRole('button', { name: 'Start recording' }))
    await screen.findByRole('status', { name: 'Review Recording in progress' })

    await user.click(screen.getByRole('button', { name: 'Stop Review Recording' }))

    await waitFor(() =>
      expect(screen.queryByRole('status', { name: 'Review Recording in progress' })).not.toBeInTheDocument(),
    )
    expect(screen.getByRole('button', { name: 'Start Review Recording' })).toBeVisible()
  })
})

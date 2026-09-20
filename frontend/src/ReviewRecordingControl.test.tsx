import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ReviewRecordingControl from './ReviewRecordingControl'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/review_recording_control.feature

const DISCLOSURE = 'Starting a Review Recording captures navigation, code discussed, and questions/decisions.'

const SNAPSHOT = {
  recordingId: 'recording-1',
  repositoryFullName: 'acme/widgets',
  pullRequestNumber: 42,
  active: true,
  audioEnabled: false,
  elapsedSeconds: 0,
  participantCount: 1,
  participantDisplayNames: ['Petros'],
}

const TAGGED_MOMENT = {
  momentId: 'moment-1',
  kind: 'QUESTION',
  reference: 'entity:OrderService',
  taggedAt: '2026-09-17T10:00:00Z',
  status: 'PENDING',
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
    http.get('/api/review-recordings/recording-1/summary', () =>
      HttpResponse.json({ durationSeconds: 42, momentCountsByKind: { QUESTION: 1 } }),
    ),
    http.post('/api/review-recordings/recording-1/moments', () => HttpResponse.json(TAGGED_MOMENT)),
    http.post('/api/review-recordings/recording-1/moments/moment-1/confirm', () => new HttpResponse(null, { status: 200 })),
    http.post('/api/review-recordings/recording-1/moments/moment-1/reject', () => new HttpResponse(null, { status: 200 })),
  )
}

async function startRecordingAndTagQuestion(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))
  await user.click(await screen.findByRole('button', { name: 'Start recording' }))
  await screen.findByRole('status', { name: 'Review Recording in progress' })
  await user.click(screen.getByRole('button', { name: 'Tag as Question' }))
  await screen.findByRole('group', { name: 'Confirm tagged moment' })
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
  })

  it('a developer tags the current moment while recording', async () => {
    mockEndpoints()
    let tagRequestBody: unknown = null
    server.use(
      http.post('/api/review-recordings/recording-1/moments', async ({ request }) => {
        tagRequestBody = await request.json()
        return HttpResponse.json(TAGGED_MOMENT)
      }),
    )
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))
    await user.click(await screen.findByRole('button', { name: 'Start recording' }))
    await screen.findByRole('status', { name: 'Review Recording in progress' })

    await user.click(screen.getByRole('button', { name: 'Tag as Question' }))

    await waitFor(() => expect(tagRequestBody).toEqual({ kind: 'QUESTION' }))
  })

  it('a developer confirms a pending moment', async () => {
    mockEndpoints()
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await startRecordingAndTagQuestion(user)

    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    await waitFor(() =>
      expect(screen.queryByRole('group', { name: 'Confirm tagged moment' })).not.toBeInTheDocument(),
    )
    expect(screen.getByRole('list', { name: 'Review timeline' })).toHaveTextContent('Question')
  })

  it('a developer rejects a pending moment', async () => {
    mockEndpoints()
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await startRecordingAndTagQuestion(user)

    await user.click(screen.getByRole('button', { name: 'Reject' }))

    await waitFor(() =>
      expect(screen.queryByRole('group', { name: 'Confirm tagged moment' })).not.toBeInTheDocument(),
    )
  })

  it("stopping a recording shows a duration and moment-count summary", async () => {
    mockEndpoints()
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await startRecordingAndTagQuestion(user)
    await user.click(screen.getByRole('button', { name: 'Confirm' }))
    await screen.findByRole('list', { name: 'Review timeline' })

    await user.click(screen.getByRole('button', { name: 'Stop Review Recording' }))

    const summary = await screen.findByRole('region', { name: 'Review summary' })
    expect(summary).toHaveTextContent('0:42')
    expect(summary).toHaveTextContent('Question: 1')
  })

  // Traces frontend/src/test/resources/features/review_recorder/audio_consent.feature (ticket #208)
  it('starting a recording without checking the audio box does not enable audio capture', async () => {
    mockEndpoints()
    let startRequestBody: unknown = null
    server.use(
      http.post('/api/review-recordings', async ({ request }) => {
        startRequestBody = await request.json()
        return HttpResponse.json({ recordingId: 'recording-1', snapshot: SNAPSHOT })
      }),
    )
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))
    await user.click(await screen.findByRole('button', { name: 'Start recording' }))

    await waitFor(() =>
      expect(startRequestBody).toEqual({ displayName: 'Petros', disclosureAcknowledged: true, audioEnabled: false }),
    )
  })

  it('checking the audio box before starting enables audio capture', async () => {
    mockEndpoints()
    let startRequestBody: unknown = null
    server.use(
      http.post('/api/review-recordings', async ({ request }) => {
        startRequestBody = await request.json()
        return HttpResponse.json({ recordingId: 'recording-1', snapshot: { ...SNAPSHOT, audioEnabled: true } })
      }),
    )
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))
    await screen.findByText(DISCLOSURE)

    await user.click(screen.getByRole('checkbox', { name: 'Also capture audio and a transcript' }))
    await user.click(screen.getByRole('button', { name: 'Start recording' }))

    await waitFor(() =>
      expect(startRequestBody).toEqual({ displayName: 'Petros', disclosureAcknowledged: true, audioEnabled: true }),
    )
  })
})

// Traces frontend/src/test/resources/features/ui_first_experience/in_person_mic_capture.feature (ticket #253)
describe('In-person single-microphone audio capture', () => {
  // A fake MediaStream/MediaRecorder pair standing in for the browser's real microphone
  // hardware and MediaRecorder API — the one genuine external boundary this suite can't cross
  // for real (no physical microphone or permission prompt exists in a test run). This fakes
  // only that boundary, never ReviewRecordingControl's or micCapture.ts's own logic.
  class FakeMediaRecorder {
    ondataavailable: ((event: { data: Blob }) => void) | null = null
    onstop: (() => void) | null = null

    constructor(public stream: unknown) {}

    start(): void {}

    stop(): void {
      this.ondataavailable?.({ data: new Blob(['fake-audio-bytes'], { type: 'audio/webm' }) })
      this.onstop?.()
    }
  }

  function stubAvailableMicrophone(): ReturnType<typeof vi.fn> {
    const getUserMedia = vi.fn().mockResolvedValue({ id: 'fake-stream' })
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } })
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    return getUserMedia
  }

  function stubPermissionDeniedMicrophone(): void {
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: vi.fn().mockRejectedValue(new DOMException('denied', 'NotAllowedError')) },
    })
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
  }

  function stubUnsupportedBrowser(): void {
    vi.stubGlobal('navigator', { mediaDevices: undefined })
    vi.stubGlobal('MediaRecorder', undefined)
  }

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('starting a recording with audio enabled begins capturing the shared microphone', async () => {
    mockEndpoints()
    const getUserMedia = stubAvailableMicrophone()
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))
    await user.click(screen.getByRole('checkbox', { name: 'Also capture audio and a transcript' }))

    await user.click(screen.getByRole('button', { name: 'Start recording' }))

    await screen.findByRole('status', { name: 'Review Recording in progress' })
    await waitFor(() => expect(getUserMedia).toHaveBeenCalled())
  })

  it('starting a recording without audio enabled never touches the microphone', async () => {
    mockEndpoints()
    const getUserMedia = stubAvailableMicrophone()
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))

    await user.click(await screen.findByRole('button', { name: 'Start recording' }))

    await screen.findByRole('status', { name: 'Review Recording in progress' })
    expect(getUserMedia).not.toHaveBeenCalled()
  })

  it('stopping a recording uploads the captured audio', async () => {
    mockEndpoints()
    stubAvailableMicrophone()
    let uploadedParticipant: string | null = null
    server.use(
      http.post('/api/review-recordings/recording-1/audio', async ({ request }) => {
        const body = await request.formData()
        uploadedParticipant = body.get('participantDisplayName') as string
        return HttpResponse.json({ ...SNAPSHOT, active: false })
      }),
    )
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))
    await user.click(screen.getByRole('checkbox', { name: 'Also capture audio and a transcript' }))
    await user.click(screen.getByRole('button', { name: 'Start recording' }))
    await screen.findByRole('status', { name: 'Review Recording in progress' })

    await user.click(screen.getByRole('button', { name: 'Stop Review Recording' }))

    await waitFor(() => expect(uploadedParticipant).toBe('Petros'))
  })

  it('microphone permission denial still lets the recording start normally', async () => {
    mockEndpoints()
    stubPermissionDeniedMicrophone()
    let audioUploaded = false
    server.use(
      http.post('/api/review-recordings/recording-1/audio', () => {
        audioUploaded = true
        return HttpResponse.json({ ...SNAPSHOT, active: false })
      }),
    )
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))
    await user.click(screen.getByRole('checkbox', { name: 'Also capture audio and a transcript' }))
    await user.click(screen.getByRole('button', { name: 'Start recording' }))
    const indicator = await screen.findByRole('status', { name: 'Review Recording in progress' })
    expect(indicator).toHaveTextContent('Recording')

    await user.click(screen.getByRole('button', { name: 'Stop Review Recording' }))

    await waitFor(() =>
      expect(screen.queryByRole('status', { name: 'Review Recording in progress' })).not.toBeInTheDocument(),
    )
    expect(audioUploaded).toBe(false)
  })

  it('an unsupported browser still lets the recording start normally', async () => {
    mockEndpoints()
    stubUnsupportedBrowser()
    let audioUploaded = false
    server.use(
      http.post('/api/review-recordings/recording-1/audio', () => {
        audioUploaded = true
        return HttpResponse.json({ ...SNAPSHOT, active: false })
      }),
    )
    render(<ReviewRecordingControl displayName="Petros" />)
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Start Review Recording' }))
    await user.click(screen.getByRole('checkbox', { name: 'Also capture audio and a transcript' }))
    await user.click(screen.getByRole('button', { name: 'Start recording' }))
    const indicator = await screen.findByRole('status', { name: 'Review Recording in progress' })
    expect(indicator).toHaveTextContent('Recording')

    await user.click(screen.getByRole('button', { name: 'Stop Review Recording' }))

    await waitFor(() =>
      expect(screen.queryByRole('status', { name: 'Review Recording in progress' })).not.toBeInTheDocument(),
    )
    expect(audioUploaded).toBe(false)
  })
})

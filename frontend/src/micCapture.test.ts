import { afterEach, describe, expect, it, vi } from 'vitest'
import { startMicCapture } from './micCapture'

// Traces frontend/src/test/resources/features/ui_first_experience/in_person_mic_capture.feature

// A fake MediaStream/MediaRecorder pair standing in for the browser's real microphone hardware
// and MediaRecorder API — the one genuine external boundary this suite can't cross for real (no
// physical microphone or permission prompt exists in a test run). This fakes only that boundary,
// never micCapture.ts's own logic.
class FakeMediaRecorder {
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  state: 'inactive' | 'recording' = 'inactive'
  stream: unknown

  constructor(stream: unknown) {
    this.stream = stream
  }

  start(): void {
    this.state = 'recording'
  }

  stop(): void {
    this.state = 'inactive'
    this.ondataavailable?.({ data: new Blob(['fake-audio-bytes'], { type: 'audio/webm' }) })
    this.onstop?.()
  }
}

// A fake MediaStream track, just real enough that startMicCapture's real cleanup logic
// (stopping every track once capture ends, to actually release the microphone) has something
// real to call — a bare `{ id: ... }` object would silently skip that behavior in a test
// without ever exercising it.
function fakeMediaStream(): MediaStream {
  const track = { stop: vi.fn() }
  return { getTracks: () => [track] } as unknown as MediaStream
}

function stubAvailableMicrophone(): void {
  vi.stubGlobal('navigator', {
    mediaDevices: {
      getUserMedia: vi.fn().mockResolvedValue(fakeMediaStream()),
    },
  })
  vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
}

function stubPermissionDenied(): void {
  vi.stubGlobal('navigator', {
    mediaDevices: {
      getUserMedia: vi.fn().mockRejectedValue(new DOMException('Permission denied', 'NotAllowedError')),
    },
  })
  vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
}

function stubUnsupportedBrowser(): void {
  vi.stubGlobal('navigator', { mediaDevices: undefined })
  vi.stubGlobal('MediaRecorder', undefined)
}

// A MediaRecorder whose constructor throws — a real browser does this (NotSupportedError) for
// a codec/MIME-type combination it won't record, even though getUserMedia already succeeded.
class ThrowingConstructorMediaRecorder {
  constructor() {
    throw new DOMException('not supported', 'NotSupportedError')
  }
}

// A MediaRecorder that constructs fine but throws from start() — an independently-fallible step
// from the constructor.
class ThrowingStartMediaRecorder {
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null

  start(): void {
    throw new DOMException('already recording', 'InvalidStateError')
  }
}

// A MediaRecorder that starts fine but throws from stop() — e.g. the browser already
// auto-stopped it because the user revoked mic permission mid-recording.
class ThrowingStopMediaRecorder {
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null

  start(): void {}

  stop(): void {
    throw new DOMException('already inactive', 'InvalidStateError')
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('startMicCapture', () => {
  it('begins capturing when the microphone is available', async () => {
    stubAvailableMicrophone()

    const session = await startMicCapture()

    expect(session).not.toBeNull()
  })

  it('produces the captured audio as a Blob once stopped', async () => {
    stubAvailableMicrophone()
    const session = await startMicCapture()

    const audio = await session!.stop()

    expect(audio).toBeInstanceOf(Blob)
    expect(audio.size).toBeGreaterThan(0)
  })

  it('returns null rather than throwing when microphone permission is denied', async () => {
    stubPermissionDenied()

    const session = await startMicCapture()

    expect(session).toBeNull()
  })

  it('returns null rather than throwing when the browser does not support microphone capture', async () => {
    stubUnsupportedBrowser()

    const session = await startMicCapture()

    expect(session).toBeNull()
  })

  it('returns null rather than throwing when the MediaRecorder constructor itself throws', async () => {
    const track = { stop: vi.fn() }
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [track] }) },
    })
    vi.stubGlobal('MediaRecorder', ThrowingConstructorMediaRecorder)

    const session = await startMicCapture()

    expect(session).toBeNull()
    expect(track.stop).toHaveBeenCalled()
  })

  it('returns null rather than throwing when MediaRecorder.start() throws', async () => {
    const track = { stop: vi.fn() }
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [track] }) },
    })
    vi.stubGlobal('MediaRecorder', ThrowingStartMediaRecorder)

    const session = await startMicCapture()

    expect(session).toBeNull()
    expect(track.stop).toHaveBeenCalled()
  })

  it('still resolves with whatever was captured when MediaRecorder.stop() throws', async () => {
    const track = { stop: vi.fn() }
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [track] }) },
    })
    vi.stubGlobal('MediaRecorder', ThrowingStopMediaRecorder)
    const session = await startMicCapture()

    const audio = await session!.stop()

    expect(audio).toBeInstanceOf(Blob)
    expect(track.stop).toHaveBeenCalled()
  })
})

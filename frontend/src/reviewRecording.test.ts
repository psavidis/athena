import { afterEach, describe, expect, it, vi } from 'vitest'
import { uploadReviewRecordingAudio } from './reviewRecording'

// Dedicated unit test for uploadReviewRecordingAudio (ticket #253/#252's shared upload
// endpoint). Spies on global fetch directly rather than going through MSW's network-level
// interception: msw/node hangs indefinitely reading a multipart body containing a Blob in this
// environment (verified with a minimal repro outside this file) — spying on fetch instead still
// exercises this function's real FormData-construction logic without hitting that limitation.
// ReviewRecordingControl.test.tsx separately verifies the real end-to-end request reaches the
// right URL as a real multipart POST.

describe('uploadReviewRecordingAudio', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts the audio and participant name as multipart form data to the recording-specific endpoint', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 200 }))
    vi.stubGlobal('fetch', fetchSpy)
    const audio = new Blob(['fake-audio-bytes'], { type: 'audio/webm' })

    await uploadReviewRecordingAudio('recording-1', 'Petros', audio)

    expect(fetchSpy).toHaveBeenCalledTimes(1)
    const [url, init] = fetchSpy.mock.calls[0]
    expect(url).toBe('/api/review-recordings/recording-1/audio')
    expect(init.method).toBe('POST')
    const body = init.body as FormData
    expect(body.get('participantDisplayName')).toBe('Petros')
    expect(body.get('file')).toBeInstanceOf(Blob)
  })

  it('URL-encodes the recording id', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 200 }))
    vi.stubGlobal('fetch', fetchSpy)

    await uploadReviewRecordingAudio('a recording/with odd chars', 'Petros', new Blob(['x']))

    const [url] = fetchSpy.mock.calls[0]
    expect(url).toBe('/api/review-recordings/a%20recording%2Fwith%20odd%20chars/audio')
  })

  it('does not throw when the upload request fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new TypeError('network error')),
    )

    await expect(uploadReviewRecordingAudio('recording-1', 'Petros', new Blob(['x']))).resolves.toBeUndefined()
  })
})

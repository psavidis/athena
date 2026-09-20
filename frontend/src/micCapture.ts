// Browser-side microphone capture for an in-person Review Recording
// (ticket #253): wraps navigator.mediaDevices.getUserMedia + MediaRecorder,
// the one place in this codebase that touches real microphone hardware.
// Every failure mode (permission denied, no microphone, browser doesn't
// support the API) resolves to `null` rather than throwing — a failed
// capture attempt must never stop the underlying Review Recording from
// starting or running normally, matching ticket #207's "a failed
// transcription/AI step does not lose the underlying review session"
// principle, extended here to capture itself.

export interface MicCaptureSession {
  /** Stops capturing and resolves with everything captured since {@link startMicCapture}. */
  stop(): Promise<Blob>
}

/**
 * Begins capturing the shared microphone, or returns `null` if capture isn't possible on this
 * browser/permission state right now (never throws) — the caller (`ReviewRecordingControl`)
 * treats `null` exactly like "audio was never enabled": the recording proceeds with no
 * transcript, rather than failing to start at all.
 */
export async function startMicCapture(): Promise<MicCaptureSession | null> {
  if (typeof navigator === 'undefined' || !navigator.mediaDevices || typeof MediaRecorder === 'undefined') {
    return null
  }
  let stream: MediaStream
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  } catch {
    // Permission denied, no microphone present, or any other getUserMedia rejection — all
    // ordinary, expected outcomes on a real machine, not exceptional ones.
    return null
  }

  let recorder: MediaRecorder
  try {
    recorder = new MediaRecorder(stream)
  } catch {
    // The MediaRecorder constructor itself can throw (e.g. NotSupportedError for a browser/
    // codec combination it won't record) even though getUserMedia already succeeded — the two
    // are independently-fallible steps. The stream was still acquired, so its tracks must be
    // released here or the microphone stays "in use" with nothing ever consuming it.
    stopAllTracks(stream)
    return null
  }

  const chunks: Blob[] = []
  recorder.ondataavailable = (event) => {
    if (event.data.size > 0) {
      chunks.push(event.data)
    }
  }

  try {
    recorder.start()
  } catch {
    // Same reasoning as the constructor above: start() can independently throw.
    stopAllTracks(stream)
    return null
  }

  return {
    stop(): Promise<Blob> {
      return new Promise((resolve) => {
        recorder.onstop = () => {
          stopAllTracks(stream)
          resolve(new Blob(chunks, { type: recorder.mimeType || 'audio/webm' }))
        }
        try {
          recorder.stop()
        } catch {
          // MediaRecorder.stop() can itself throw (e.g. the browser already auto-stopped the
          // recorder because the user revoked mic permission mid-recording) — this must not
          // become an unhandled rejection in the caller. Whatever was captured before this
          // point is still worth uploading rather than discarding entirely.
          stopAllTracks(stream)
          resolve(new Blob(chunks, { type: recorder.mimeType || 'audio/webm' }))
        }
      })
    },
  }
}

function stopAllTracks(stream: MediaStream): void {
  for (const track of stream.getTracks()) {
    track.stop()
  }
}

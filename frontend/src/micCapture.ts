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

  const recorder = new MediaRecorder(stream)
  const chunks: Blob[] = []
  recorder.ondataavailable = (event) => {
    if (event.data.size > 0) {
      chunks.push(event.data)
    }
  }
  recorder.start()

  return {
    stop(): Promise<Blob> {
      return new Promise((resolve) => {
        recorder.onstop = () => {
          for (const track of stream.getTracks()) {
            track.stop()
          }
          resolve(new Blob(chunks, { type: recorder.mimeType || 'audio/webm' }))
        }
        recorder.stop()
      })
    },
  }
}

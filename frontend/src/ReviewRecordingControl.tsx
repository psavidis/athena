import { useEffect, useRef, useState } from 'react'
import {
  fetchCaptureDisclosure,
  startReviewRecording,
  stopReviewRecording,
  type ReviewRecordingSnapshot,
} from './reviewRecording'
import { PrimaryButton, SecondaryButton } from './ui'

/**
 * The explicit Start/Stop Review Recording control (ticket #203):
 * shows the capture disclosure before starting, then a subtle persistent
 * indicator (elapsed time, participant count) while active — never a UI
 * dominated by recording controls. Session lifecycle shell only; no
 * audio/transcription/semantic-event UI yet.
 */
export default function ReviewRecordingControl({ displayName }: { displayName: string }) {
  const [disclosureOpen, setDisclosureOpen] = useState(false)
  const [disclosureText, setDisclosureText] = useState('')
  const [snapshot, setSnapshot] = useState<ReviewRecordingSnapshot | null>(null)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const tickRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (!snapshot?.active) {
      if (tickRef.current) {
        clearInterval(tickRef.current)
        tickRef.current = null
      }
      return
    }
    setElapsedSeconds(snapshot.elapsedSeconds)
    tickRef.current = setInterval(() => setElapsedSeconds((seconds) => seconds + 1), 1000)
    return () => {
      if (tickRef.current) {
        clearInterval(tickRef.current)
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [snapshot?.active, snapshot?.recordingId])

  async function openDisclosure() {
    setError(null)
    const text = await fetchCaptureDisclosure()
    setDisclosureText(text)
    setDisclosureOpen(true)
  }

  async function acknowledgeAndStart() {
    setDisclosureOpen(false)
    try {
      const result = await startReviewRecording(displayName, true)
      setSnapshot(result.snapshot)
    } catch {
      setError('Select a Pull Request or Diff before starting a Review Recording.')
    }
  }

  async function stop() {
    if (!snapshot) return
    const updated = await stopReviewRecording(snapshot.recordingId)
    setSnapshot(updated)
  }

  if (snapshot?.active) {
    return (
      <div
        role="status"
        aria-label="Review Recording in progress"
        className="flex items-center gap-2 rounded-full border border-canvas-line-strong bg-canvas-paper-raised px-3 py-1 text-xs text-canvas-ink-soft"
      >
        <span className="h-2 w-2 animate-pulse rounded-full bg-red-500" aria-hidden="true" />
        <span>Recording</span>
        <span className="font-mono tabular-nums">{formatElapsed(elapsedSeconds)}</span>
        <span aria-label="participant count">· {snapshot.participantCount}</span>
        <SecondaryButton onClick={stop} aria-label="Stop Review Recording">
          Stop
        </SecondaryButton>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <PrimaryButton onClick={openDisclosure}>Start Review Recording</PrimaryButton>
      {error && <p className="text-xs text-red-600">{error}</p>}
      {disclosureOpen && (
        <div
          role="dialog"
          aria-label="Review Recording disclosure"
          className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 p-6"
        >
          <div className="max-w-md rounded-2xl border border-canvas-line-strong bg-canvas-paper-raised p-5 shadow-[var(--shadow-canvas-lift)]">
            <h2 className="mb-2 font-display text-base font-semibold text-canvas-ink">
              Before you start recording
            </h2>
            <p className="mb-4 text-sm text-canvas-ink-soft">{disclosureText}</p>
            <div className="flex justify-end gap-2">
              <SecondaryButton onClick={() => setDisclosureOpen(false)}>Cancel</SecondaryButton>
              <PrimaryButton onClick={acknowledgeAndStart}>Start recording</PrimaryButton>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function formatElapsed(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

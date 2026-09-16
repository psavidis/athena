import { useEffect, useRef, useState } from 'react'
import {
  confirmMoment,
  editMoment,
  fetchCaptureDisclosure,
  fetchSummary,
  rejectMoment,
  startReviewRecording,
  stopReviewRecording,
  tagMoment,
  type Moment,
  type MomentKind,
  type ReviewRecordingSnapshot,
  type ReviewRecordingSummary,
} from './reviewRecording'
import { PrimaryButton, SecondaryButton } from './ui'

const MOMENT_KINDS: { kind: MomentKind; label: string }[] = [
  { kind: 'INSIGHT', label: 'Insight' },
  { kind: 'QUESTION', label: 'Question' },
  { kind: 'CONCERN', label: 'Concern' },
  { kind: 'DECISION', label: 'Decision' },
  { kind: 'ACTION', label: 'Action' },
  { kind: 'VERIFICATION', label: 'Verification' },
]

/**
 * The explicit Start/Stop Review Recording control (ticket #203):
 * shows the capture disclosure before starting, then a subtle persistent
 * indicator (elapsed time, participant count) while active — never a UI
 * dominated by recording controls. Session lifecycle shell only; no
 * audio/transcription/semantic-event UI yet.
 */
export default function ReviewRecordingControl({
  displayName,
  onNavigateToReference,
}: {
  displayName: string
  onNavigateToReference?: (reference: string) => void
}) {
  const [disclosureOpen, setDisclosureOpen] = useState(false)
  const [disclosureText, setDisclosureText] = useState('')
  const [snapshot, setSnapshot] = useState<ReviewRecordingSnapshot | null>(null)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [timeline, setTimeline] = useState<Moment[]>([])
  const [pendingMoment, setPendingMoment] = useState<Moment | null>(null)
  const [summary, setSummary] = useState<ReviewRecordingSummary | null>(null)
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
    const recordingId = snapshot.recordingId
    const updated = await stopReviewRecording(recordingId)
    setSnapshot(updated)
    setSummary(await fetchSummary(recordingId))
  }

  async function tag(kind: MomentKind) {
    if (!snapshot) return
    const moment = await tagMoment(snapshot.recordingId, kind)
    setPendingMoment(moment)
  }

  async function confirmPending() {
    if (!snapshot || !pendingMoment) return
    await confirmMoment(snapshot.recordingId, pendingMoment.momentId)
    setTimeline((current) => [...current, { ...pendingMoment, status: 'CONFIRMED' }])
    setPendingMoment(null)
  }

  async function rejectPending() {
    if (!snapshot || !pendingMoment) return
    await rejectMoment(snapshot.recordingId, pendingMoment.momentId)
    setPendingMoment(null)
  }

  async function editPending(kind: MomentKind) {
    if (!snapshot || !pendingMoment) return
    await editMoment(snapshot.recordingId, pendingMoment.momentId, kind)
    setPendingMoment({ ...pendingMoment, kind })
  }

  if (snapshot?.active) {
    return (
      <div className="flex flex-col items-start gap-1.5">
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
        <div role="group" aria-label="Tag this moment" className="flex flex-wrap gap-1">
          {MOMENT_KINDS.map(({ kind, label }) => (
            <button
              key={kind}
              type="button"
              onClick={() => tag(kind)}
              aria-label={`Tag as ${label}`}
              className="rounded-full border border-canvas-line px-2 py-0.5 text-[11px] text-canvas-ink-faint transition-colors duration-150 hover:border-accent hover:text-canvas-ink"
            >
              {label}
            </button>
          ))}
        </div>
        {pendingMoment && (
          <div
            role="group"
            aria-label="Confirm tagged moment"
            className="flex items-center gap-2 rounded-lg border border-canvas-line-strong bg-canvas-paper-raised px-3 py-1.5 text-xs text-canvas-ink-soft"
          >
            <span>
              {MOMENT_KINDS.find((m) => m.kind === pendingMoment.kind)?.label ?? pendingMoment.kind}?
            </span>
            <select
              aria-label="Change moment kind"
              value={pendingMoment.kind}
              onChange={(e) => editPending(e.target.value as MomentKind)}
              className="rounded border border-canvas-line bg-canvas-paper px-1 py-0.5 text-xs"
            >
              {MOMENT_KINDS.map(({ kind, label }) => (
                <option key={kind} value={kind}>
                  {label}
                </option>
              ))}
            </select>
            <SecondaryButton onClick={confirmPending}>Confirm</SecondaryButton>
            <SecondaryButton onClick={rejectPending}>Reject</SecondaryButton>
          </div>
        )}
        {timeline.length > 0 && (
          <ol aria-label="Review timeline" className="flex flex-col gap-1">
            {timeline.map((moment) => (
              <li key={moment.momentId}>
                <button
                  type="button"
                  onClick={() => moment.reference && onNavigateToReference?.(moment.reference)}
                  className="text-left text-[11px] text-canvas-ink-faint underline-offset-2 hover:text-canvas-ink hover:underline"
                >
                  {MOMENT_KINDS.find((m) => m.kind === moment.kind)?.label ?? moment.kind}
                  {moment.reference ? ` — ${moment.reference}` : ''}
                </button>
              </li>
            ))}
          </ol>
        )}
      </div>
    )
  }

  if (summary) {
    return (
      <div
        role="region"
        aria-label="Review summary"
        className="flex flex-col gap-1 rounded-lg border border-canvas-line-strong bg-canvas-paper-raised p-3 text-xs text-canvas-ink-soft"
      >
        <p>Duration: {formatElapsed(summary.durationSeconds)}</p>
        <ul className="flex flex-col gap-0.5">
          {Object.entries(summary.momentCountsByKind).map(([kind, count]) => (
            <li key={kind}>
              {MOMENT_KINDS.find((m) => m.kind === kind)?.label ?? kind}: {count}
            </li>
          ))}
        </ul>
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

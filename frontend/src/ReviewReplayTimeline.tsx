import { useEffect, useState } from 'react'
import { MOMENT_KINDS, type Moment } from './reviewRecording'
import {
  fetchReplayMoments,
  groupMomentsByReference,
  nextMomentId,
  previousMomentId,
  type MomentGroup,
} from './reviewReplay'
import { SecondaryButton } from './ui'

function momentLabel(kind: Moment['kind']): string {
  return MOMENT_KINDS.find((m) => m.kind === kind)?.label ?? kind
}

/**
 * Review Replay's semantic timeline (ticket #211): a persisted recording's
 * moments in chronological order, grouped by shared reference (a question
 * and the decision/insight it led to read as one thread), with
 * next/previous and jump-to-moment navigation. No semantic canvas
 * integration yet (next ticket) — navigating just moves which moment is
 * highlighted here.
 */
export default function ReviewReplayTimeline({ recordingId }: { recordingId: string }) {
  const [moments, setMoments] = useState<Moment[]>([])
  const [currentMomentId, setCurrentMomentId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchReplayMoments(recordingId)
      .then((loaded) => {
        if (cancelled) return
        setMoments(loaded)
        setCurrentMomentId(loaded.length > 0 ? loaded[0].momentId : null)
      })
      .catch(() => {
        if (!cancelled) setError('Could not load this Replay.')
      })
    return () => {
      cancelled = true
    }
  }, [recordingId])

  if (error) {
    return <p role="alert">{error}</p>
  }

  if (moments.length === 0) {
    return (
      <div role="region" aria-label="Replay timeline">
        <p>This Replay has no recorded moments.</p>
      </div>
    )
  }

  const groups = groupMomentsByReference(moments)

  return (
    <div role="region" aria-label="Replay timeline">
      <div role="group" aria-label="Timeline navigation">
        <SecondaryButton onClick={() => setCurrentMomentId((id) => (id ? previousMomentId(moments, id) : id))}>
          Previous
        </SecondaryButton>
        <SecondaryButton onClick={() => setCurrentMomentId((id) => (id ? nextMomentId(moments, id) : id))}>
          Next
        </SecondaryButton>
      </div>
      <ol>
        {groups.map((group) => (
          <MomentGroupRow
            key={group.moments[0].momentId}
            group={group}
            currentMomentId={currentMomentId}
            onJump={setCurrentMomentId}
          />
        ))}
      </ol>
    </div>
  )
}

function MomentGroupRow({
  group,
  currentMomentId,
  onJump,
}: {
  group: MomentGroup
  currentMomentId: string | null
  onJump: (momentId: string) => void
}) {
  return (
    <li role="group" aria-label={group.reference ?? 'Ungrouped moment'}>
      <ul>
        {group.moments.map((moment) => (
          <li key={moment.momentId}>
            <button
              type="button"
              aria-current={moment.momentId === currentMomentId}
              onClick={() => onJump(moment.momentId)}
            >
              {momentLabel(moment.kind)}
            </button>
          </li>
        ))}
      </ul>
    </li>
  )
}

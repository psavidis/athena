import { useEffect, useState } from 'react'
import { MOMENT_KINDS, type Moment } from './reviewRecording'
import {
  fetchReplayModulesByReference,
  fetchReplayMoments,
  groupMomentsByReference,
  moduleForMoment,
  nextMomentId,
  previousMomentId,
  type MomentGroup,
} from './reviewReplay'
import { SecondaryButton } from './ui'

function momentLabel(kind: Moment['kind']): string {
  return MOMENT_KINDS.find((m) => m.kind === kind)?.label ?? kind
}

/**
 * Review Replay's semantic timeline (ticket #211, canvas integration in
 * #212): a persisted recording's moments in chronological order, grouped
 * by shared reference (a question and the decision/insight it led to read
 * as one thread), with next/previous and jump-to-moment navigation.
 * Selecting a moment focuses the Semantic Canvas on the module its
 * entity belongs to, via `onFocusModule` — mirroring Context Rewind's own
 * `onOpenModule`/Live Session's `sharedTerritory` external-navigation
 * contract (see #212's own re-scoping) rather than inventing a new one.
 * A moment whose module can't be determined, or whose reference no
 * longer resolves, simply doesn't call `onFocusModule` — the canvas is
 * left wherever it already was.
 */
export default function ReviewReplayTimeline({
  recordingId,
  onFocusModule,
}: {
  recordingId: string
  onFocusModule?: (moduleName: string) => void
}) {
  const [moments, setMoments] = useState<Moment[]>([])
  const [currentMomentId, setCurrentMomentId] = useState<string | null>(null)
  const [modulesByReference, setModulesByReference] = useState<Map<string, string>>(new Map())
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
    fetchReplayModulesByReference(recordingId)
      .then((loaded) => {
        if (!cancelled) setModulesByReference(loaded)
      })
      .catch(() => {
        // Module focus is a secondary enhancement over the timeline itself
        // (ticket #212's own scope note: "leaves the canvas unchanged" is
        // the correct fallback) — a failure here must not block the
        // timeline from rendering, so it's swallowed rather than surfaced
        // via `error`.
      })
    return () => {
      cancelled = true
    }
  }, [recordingId])

  function selectMoment(momentId: string) {
    setCurrentMomentId(momentId)
    const moment = moments.find((m) => m.momentId === momentId)
    if (!moment || !onFocusModule) {
      return
    }
    const moduleName = moduleForMoment(moment, modulesByReference)
    if (moduleName) {
      onFocusModule(moduleName)
    }
  }

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
        <SecondaryButton
          onClick={() => currentMomentId && selectMoment(previousMomentId(moments, currentMomentId))}
        >
          Previous
        </SecondaryButton>
        <SecondaryButton onClick={() => currentMomentId && selectMoment(nextMomentId(moments, currentMomentId))}>
          Next
        </SecondaryButton>
      </div>
      <ol>
        {groups.map((group) => (
          <MomentGroupRow
            key={group.moments[0].momentId}
            group={group}
            currentMomentId={currentMomentId}
            onJump={selectMoment}
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

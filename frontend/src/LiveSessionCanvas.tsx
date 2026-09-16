import { useEffect, useRef, useState } from 'react'
import type { ImportedPullRequest } from './api'
import { territoryItemId } from './canvasItemId'
import {
  addLiveComment,
  endLiveSession,
  exploreIndependently,
  followSharedView,
  getLiveComments,
  joinLiveSession,
  leaveLiveSession,
  presentFocus,
  startLiveSession,
  subscribeToLiveSession,
  takeControl,
  type CanvasFocus,
  type LiveComment,
  type LiveReviewSessionSnapshot,
} from './liveSession'
import { liveSessionPath, parseLiveSessionPath } from './liveSessionUrl'
import SemanticCanvasPage from './SemanticCanvasPage'
import { PrimaryButton, SecondaryButton } from './ui'

function territoryFocus(moduleName: string | undefined): CanvasFocus {
  return moduleName
    ? {
        zoomLevel: 'TERRITORY',
        selectedEntityId: territoryItemId(moduleName),
        selectedChangeKey: null,
        navigationContext: moduleName,
      }
    : { zoomLevel: 'OVERVIEW', selectedEntityId: null, selectedChangeKey: null, navigationContext: null }
}

function territoryFromFocus(focus: CanvasFocus | null | undefined): string | undefined {
  const id = focus?.selectedEntityId
  if (!id) return undefined
  const match = id.match(/^territory:(.+)$/)
  return match ? match[1] : undefined
}

/**
 * Wraps {@link SemanticCanvasPage} with Live Code Review Session state
 * (ticket #158): starting/joining a session, subscribing to its real-time
 * updates, and bridging the canvas's own territory navigation with the
 * session's shared focus. Drop-in replacement for rendering
 * `SemanticCanvasPage` directly — same props, App.tsx just swaps which one
 * it renders.
 */
export default function LiveSessionCanvas(props: {
  pullRequest: ImportedPullRequest | null
  picker?: React.ReactNode
  onNotConnected: () => void
  onNoPullRequestSelected: () => void
}) {
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [participantId, setParticipantId] = useState<string | null>(null)
  const [snapshot, setSnapshot] = useState<LiveReviewSessionSnapshot | null>(null)
  // A shared link (/live/:id) opened this app instance — prompt for a display
  // name to join. Read once, directly, as the initial state value rather
  // than via a mount effect (the URL a fresh render mounted with is already
  // everything this needs).
  const [joinPromptSessionId, setJoinPromptSessionId] = useState<string | null>(() =>
    parseLiveSessionPath(window.location.pathname),
  )
  const [error, setError] = useState<string | null>(null)
  const localTerritoryRef = useRef<string | undefined>(undefined)

  useEffect(() => {
    if (!sessionId || !participantId) {
      return
    }
    return subscribeToLiveSession(sessionId, participantId, setSnapshot)
  }, [sessionId, participantId])

  const me = snapshot?.participants.find((p) => p.participantId === participantId)
  const isPresenter = snapshot !== null && snapshot.presenterId === participantId
  const isFollowing = me?.mode === 'FOLLOWING'
  const isExploring = me?.mode === 'EXPLORING'
  const sharedTerritory = territoryFromFocus(snapshot?.sharedFocus)

  function backToPlainUrl() {
    const path = parseLiveSessionPath(window.location.pathname)
    if (path) {
      window.history.replaceState(null, '', '/')
    }
  }

  async function handleStart(displayName: string) {
    setError(null)
    try {
      const result = await startLiveSession(displayName)
      setSessionId(result.sessionId)
      setParticipantId(result.participantId)
      setSnapshot(result.snapshot)
      window.history.replaceState(null, '', liveSessionPath(result.sessionId))
    } catch {
      setError('Select a Pull Request before starting a Live Code Review Session.')
    }
  }

  async function handleJoin(displayName: string) {
    if (!joinPromptSessionId) {
      return
    }
    setError(null)
    try {
      const result = await joinLiveSession(joinPromptSessionId, displayName)
      setSessionId(result.sessionId)
      setParticipantId(result.participantId)
      setSnapshot(result.snapshot)
      setJoinPromptSessionId(null)
    } catch {
      setError('This Live Code Review Session could not be found.')
      setJoinPromptSessionId(null)
      backToPlainUrl()
    }
  }

  async function handleLeave() {
    if (sessionId && participantId) {
      await leaveLiveSession(sessionId, participantId)
    }
    setSessionId(null)
    setParticipantId(null)
    setSnapshot(null)
    backToPlainUrl()
  }

  function handleLocalTerritoryChange(moduleName: string | undefined) {
    localTerritoryRef.current = moduleName
    if (!sessionId || !participantId) {
      return
    }
    if (isPresenter) {
      presentFocus(sessionId, participantId, territoryFocus(moduleName))
    } else if (isExploring) {
      exploreIndependently(sessionId, participantId, territoryFocus(moduleName))
    }
  }

  return (
    <>
      {joinPromptSessionId && <JoinLiveSessionPrompt onJoin={handleJoin} onCancel={() => setJoinPromptSessionId(null)} />}
      <SemanticCanvasPage
        {...props}
        liveSessionPanel={
          <LiveSessionPanel
            snapshot={snapshot}
            participantId={participantId}
            error={error}
            onStart={handleStart}
            onLeave={handleLeave}
            onTakeControl={() => sessionId && participantId && takeControl(sessionId, participantId)}
            onExplore={() =>
              sessionId &&
              participantId &&
              exploreIndependently(sessionId, participantId, territoryFocus(localTerritoryRef.current))
            }
            onReturnToShared={() => sessionId && participantId && followSharedView(sessionId, participantId)}
            onEnd={() => sessionId && participantId && endLiveSession(sessionId, participantId).then(handleLeave)}
          />
        }
        liveSession={
          sessionId && participantId
            ? { isPresenter, isFollowing, sharedTerritory, onLocalTerritoryChange: handleLocalTerritoryChange }
            : undefined
        }
      />
    </>
  )
}

function JoinLiveSessionPrompt({
  onJoin,
  onCancel,
}: {
  onJoin: (displayName: string) => void
  onCancel: () => void
}) {
  const [displayName, setDisplayName] = useState('')
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="w-full max-w-sm rounded-xl bg-paper-raised p-6 shadow-xl">
        <h2 className="mb-1 text-lg font-semibold text-ink-900">Join Live Code Review Session</h2>
        <p className="mb-4 text-sm text-ink-700">Enter your name to join the other reviewers.</p>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            onJoin(displayName.trim() || 'Reviewer')
          }}
          className="space-y-3"
        >
          <input
            autoFocus
            aria-label="Your name"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="Your name"
            className="w-full rounded-lg border border-ink-200 bg-paper px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent"
          />
          <div className="flex justify-end gap-2">
            <SecondaryButton type="button" onClick={onCancel}>
              Cancel
            </SecondaryButton>
            <PrimaryButton type="submit">Join session</PrimaryButton>
          </div>
        </form>
      </div>
    </div>
  )
}

function LiveSessionPanel({
  snapshot,
  participantId,
  error,
  onStart,
  onLeave,
  onTakeControl,
  onExplore,
  onReturnToShared,
  onEnd,
}: {
  snapshot: LiveReviewSessionSnapshot | null
  participantId: string | null
  error: string | null
  onStart: (displayName: string) => void
  onLeave: () => void
  onTakeControl: () => void
  onExplore: () => void
  onReturnToShared: () => void
  onEnd: () => void
}) {
  const [showComments, setShowComments] = useState(false)
  const me = snapshot?.participants.find((p) => p.participantId === participantId)
  const isCreator = snapshot !== null && snapshot.creatorId === participantId

  if (!snapshot) {
    return <StartLiveSessionButton onStart={onStart} error={error} />
  }

  const shareUrl = typeof window !== 'undefined' ? `${window.location.origin}${liveSessionPath(snapshot.sessionId)}` : ''

  return (
    <div className="flex items-center gap-2.5">
      <div className="flex -space-x-1.5" aria-label="Participants in this Live Code Review Session">
        {snapshot.participants.map((p) => (
          <span
            key={p.participantId}
            title={`${p.displayName}${p.participantId === snapshot.presenterId ? ' (presenting)' : p.mode === 'EXPLORING' ? ' (exploring independently)' : ''}${p.connected ? '' : ' (disconnected)'}`}
            className={`flex h-7 w-7 items-center justify-center rounded-full border-2 text-[10px] font-semibold uppercase ${
              p.connected ? 'border-canvas-paper-raised bg-canvas-gold-soft text-canvas-gold-deep' : 'border-canvas-paper-raised bg-canvas-line text-canvas-ink-faint opacity-50'
            } ${p.participantId === snapshot.presenterId ? 'ring-2 ring-canvas-gold' : ''}`}
          >
            {p.displayName.slice(0, 2)}
          </span>
        ))}
      </div>
      <button
        type="button"
        onClick={() => navigator.clipboard?.writeText(shareUrl)}
        title={shareUrl}
        className="rounded-lg border border-canvas-line-strong bg-canvas-paper-raised px-2.5 py-1.5 text-xs text-canvas-ink-soft hover:border-canvas-gold"
      >
        Copy share link
      </button>
      {me?.mode !== 'PRESENTING' && (
        <button
          type="button"
          onClick={onTakeControl}
          className="rounded-lg border border-canvas-line-strong bg-canvas-paper-raised px-2.5 py-1.5 text-xs text-canvas-ink-soft hover:border-canvas-gold"
        >
          Take control
        </button>
      )}
      {me?.mode === 'FOLLOWING' && (
        <button
          type="button"
          onClick={onExplore}
          className="rounded-lg border border-canvas-line-strong bg-canvas-paper-raised px-2.5 py-1.5 text-xs text-canvas-ink-soft hover:border-canvas-gold"
        >
          Explore on your own
        </button>
      )}
      {me?.mode === 'EXPLORING' && (
        <button
          type="button"
          onClick={onReturnToShared}
          className="rounded-lg border border-canvas-gold bg-canvas-gold-soft px-2.5 py-1.5 text-xs font-semibold text-canvas-gold-deep"
        >
          Return to shared view
        </button>
      )}
      <button
        type="button"
        onClick={() => setShowComments((current) => !current)}
        aria-expanded={showComments}
        className="rounded-lg border border-canvas-line-strong bg-canvas-paper-raised px-2.5 py-1.5 text-xs text-canvas-ink-soft hover:border-canvas-gold"
      >
        Session comments
      </button>
      {showComments && snapshot && participantId && (
        <LiveCommentsPopover sessionId={snapshot.sessionId} participantId={participantId} revision={snapshot.revision}
          onClose={() => setShowComments(false)} />
      )}
      {isCreator ? (
        <SecondaryButton onClick={onEnd}>End session</SecondaryButton>
      ) : (
        <SecondaryButton onClick={onLeave}>Leave session</SecondaryButton>
      )}
    </div>
  )
}

function StartLiveSessionButton({ onStart, error }: { onStart: (displayName: string) => void; error: string | null }) {
  const [prompting, setPrompting] = useState(false)
  const [displayName, setDisplayName] = useState('')

  if (!prompting) {
    return (
      <div className="flex flex-col items-end">
        <PrimaryButton onClick={() => setPrompting(true)}>Start Live Review Session</PrimaryButton>
        {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
      </div>
    )
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        setPrompting(false)
        onStart(displayName.trim() || 'Reviewer')
      }}
      className="flex items-center gap-1.5"
    >
      <input
        autoFocus
        aria-label="Your name"
        value={displayName}
        onChange={(e) => setDisplayName(e.target.value)}
        placeholder="Your name"
        className="w-32 rounded-lg border border-canvas-line-strong bg-canvas-paper-raised px-2.5 py-1.5 text-xs text-canvas-ink outline-none focus:border-canvas-gold"
      />
      <PrimaryButton type="submit">Start</PrimaryButton>
    </form>
  )
}

function LiveCommentsPopover({
  sessionId,
  participantId,
  revision,
  onClose,
}: {
  sessionId: string
  participantId: string
  revision: number
  onClose: () => void
}) {
  const [comments, setComments] = useState<LiveComment[]>([])
  const [text, setText] = useState('')

  useEffect(() => {
    getLiveComments(sessionId).then(setComments)
  }, [sessionId, revision])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!text.trim()) {
      return
    }
    const updated = await addLiveComment(sessionId, participantId, text.trim())
    setComments(updated)
    setText('')
  }

  return (
    <div className="absolute right-4 top-14 z-40 w-80 rounded-xl border border-canvas-line-strong bg-canvas-paper-raised p-3 shadow-lg">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-xs font-semibold text-canvas-ink">Session comments</span>
        <button type="button" onClick={onClose} className="text-xs text-canvas-ink-faint hover:text-canvas-ink">
          Close
        </button>
      </div>
      <ul className="mb-2 max-h-48 space-y-1.5 overflow-y-auto">
        {comments.length === 0 && <li className="text-xs text-canvas-ink-faint">No comments yet.</li>}
        {comments.map((c) => (
          <li key={c.id} className="rounded-lg bg-canvas-paper px-2 py-1.5 text-xs text-canvas-ink">
            <span className="font-semibold">{c.author}: </span>
            {c.text}
          </li>
        ))}
      </ul>
      <form onSubmit={submit} className="flex gap-1.5">
        <input
          aria-label="Comment"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Add a comment…"
          className="flex-1 rounded-lg border border-canvas-line-strong bg-canvas-paper px-2 py-1.5 text-xs text-canvas-ink outline-none focus:border-canvas-gold"
        />
        <PrimaryButton type="submit" className="px-2.5 py-1.5 text-xs">
          Post
        </PrimaryButton>
      </form>
    </div>
  )
}

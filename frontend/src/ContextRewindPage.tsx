import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  getContextRewind,
  getModuleTopology,
  getPullRequestReview,
  NoPullRequestSelectedError,
  type ContextRewind,
  type PullRequestEvidence,
  type TimelineEvent,
} from './api'
import { BackLink, ErrorState, LoadingState, PageHeading, PageShell, PrimaryButton, SecondaryButton, SectionLabel } from './ui'

/** The date an event happened, formatted deterministically (no locale-dependent wording) for
 * both the timeline and the evidence panel. */
function eventDate(occurredAt: string): string {
  return occurredAt.slice(0, 10)
}

/** Context Rewind's three semantic-zoom stops (ticket #188): Orientation shows only where the
 * entity sits, Overview is ticket #187's own timeline/Pull-Requests/narrative view, and Detail
 * focuses on a single selected event's own evidence. */
type ZoomLevel = 'orientation' | 'overview' | 'detail'

/** The fixed set of "Explain Why" quick actions (ticket #193) — no free-text prompt. */
const QUICK_ACTIONS = [
  'Why does this exist?',
  'Why was this introduced?',
  'Why was this changed?',
  'Why is it implemented this way?',
  'What happened here?',
  'What did reviewers question?',
  'What was decided?',
  'What changed since I last saw this?',
] as const

const WHY_QUESTIONS: ReadonlySet<string> = new Set(QUICK_ACTIONS.slice(0, 4))

/** Resolves a quick action using data Context Rewind has already fetched — no new backend calls,
 * no free-text Q&A. Evidence-first: a "Why" answer is always paired with what it's based on. */
function answerQuickAction(question: string, data: ContextRewind): { text: string; evidence?: string } {
  if (WHY_QUESTIONS.has(question)) {
    if (!data.aiNarrative) {
      return { text: 'Not enough information is available to answer that.' }
    }
    const eventCount = data.evolutionTimeline.length
    const prCount = data.pullRequestReferences.length
    return {
      text: data.aiNarrative,
      evidence: `Based on ${eventCount} recorded event${eventCount === 1 ? '' : 's'} and ${prCount} Pull Request${prCount === 1 ? '' : 's'}`,
    }
  }
  if (question === 'What happened here?') {
    return data.evolutionTimeline.length > 0
      ? { text: `${data.evolutionTimeline.length} recorded event(s) — see the timeline above after zooming in.` }
      : { text: 'No recorded history.' }
  }
  if (question === 'What did reviewers question?') {
    return data.pullRequestReferences.length > 0
      ? { text: `See the review for Pull Request ${data.pullRequestReferences[0].number}.` }
      : { text: 'No Pull Request is recorded for this entity.' }
  }
  if (question === 'What was decided?') {
    return { text: 'Nothing is recorded for decisions.' }
  }
  return { text: 'Use the "Catch me up" prompt below.' }
}

/** Context Rewind's foundational story timeline & evidence panel (ticket #187), reached through
 * a semantic-zoom progression (ticket #188) so the amount of information increases as the
 * developer zooms in rather than presenting everything at once. */
export default function ContextRewindPage({
  entityName,
  onBack,
  moduleName,
  conceptName,
  onOpenModule,
}: {
  entityName: string
  onBack: () => void
  /** Where this entity sits, shown at the Orientation level (ticket #188) — both optional since
   * not every Context Rewind entry point can supply them yet; Orientation falls back to just the
   * entity name when absent. */
  moduleName?: string
  conceptName?: string
  /** Returns to the Semantic Canvas focused on a related module selected from the Context Map
   * (ticket #191) — optional since the Context Map itself is only offered when `moduleName` is
   * known. */
  onOpenModule?: (moduleName: string) => void
}) {
  const [catchUpSince, setCatchUpSince] = useState<string | undefined>(undefined)
  const [catchUpDraft, setCatchUpDraft] = useState('')
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['context-rewind', entityName, catchUpSince],
    queryFn: () => getContextRewind(entityName, catchUpSince),
    retry: false,
  })
  const [selectedEvent, setSelectedEvent] = useState<TimelineEvent | undefined>(undefined)
  const [zoomLevel, setZoomLevel] = useState<ZoomLevel>('orientation')
  const [showingMap, setShowingMap] = useState(false)
  const [reviewingPr, setReviewingPr] = useState<PullRequestEvidence | undefined>(undefined)
  const [activeQuestion, setActiveQuestion] = useState<string | undefined>(undefined)

  if (isError) {
    if (error instanceof NoPullRequestSelectedError) {
      return <ErrorState message="Select a pull request first to use Context Rewind." />
    }
    return <ErrorState message={`Could not load Context Rewind for ${entityName}.`} />
  }

  if (isLoading || !data) {
    return <LoadingState />
  }

  if (data.insufficientHistoryMessage) {
    return (
      <PageShell>
        <BackLink onClick={onBack}>← Back</BackLink>
        <PageHeading eyebrow="Context Rewind" title={data.entityName} />
        <p className="text-sm text-ink-500">{data.insufficientHistoryMessage}</p>
      </PageShell>
    )
  }

  if (showingMap && moduleName) {
    return <ContextMap moduleName={moduleName} onBack={() => setShowingMap(false)} onOpenModule={onOpenModule} />
  }

  if (reviewingPr) {
    return <PullRequestReviewView reference={reviewingPr} onBack={() => setReviewingPr(undefined)} />
  }

  if (zoomLevel === 'orientation') {
    const breadcrumb = moduleName && conceptName ? `${moduleName} › ${conceptName} › ${entityName}` : entityName
    return (
      <PageShell>
        <BackLink onClick={onBack}>← Back</BackLink>
        <PageHeading eyebrow="Context Rewind" title={breadcrumb} />
        <div className="mb-6 flex gap-2">
          <PrimaryButton onClick={() => setZoomLevel('overview')}>Zoom in</PrimaryButton>
          {moduleName && <SecondaryButton onClick={() => setShowingMap(true)}>Context Map</SecondaryButton>}
        </div>
        <div className="flex items-end gap-2">
          <div>
            <label htmlFor="catch-up-since" className="mb-1 block text-sm text-ink-700">
              Catch me up since
            </label>
            <input
              id="catch-up-since"
              type="text"
              placeholder="YYYY-MM-DD"
              value={catchUpDraft}
              onChange={(e) => setCatchUpDraft(e.target.value)}
              className="rounded-lg border border-ink-200 px-3 py-2 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <SecondaryButton
            disabled={catchUpDraft.trim().length === 0}
            onClick={() => {
              setCatchUpSince(catchUpDraft.trim())
              setZoomLevel('overview')
            }}
          >
            Catch me up
          </SecondaryButton>
        </div>

        <div className="mt-6">
          <SectionLabel>Explain Why</SectionLabel>
          <div className="mb-3 flex flex-wrap gap-2">
            {QUICK_ACTIONS.map((question) => (
              <button
                key={question}
                type="button"
                onClick={() => setActiveQuestion(question)}
                className="rounded-lg border border-ink-200 px-2.5 py-1 text-xs font-medium text-ink-700 hover:border-accent hover:bg-ink-100"
              >
                {question}
              </button>
            ))}
          </div>
          {activeQuestion &&
            (() => {
              const answer = answerQuickAction(activeQuestion, data)
              return (
                <div className="rounded-lg border border-ink-200 bg-paper-raised px-4 py-3">
                  <p className="mb-1 text-sm text-ink-700">{answer.text}</p>
                  {answer.evidence && <p className="text-xs text-ink-500">{answer.evidence}</p>}
                </div>
              )
            })()}
        </div>
      </PageShell>
    )
  }

  if (zoomLevel === 'detail' && selectedEvent) {
    return (
      <PageShell>
        <BackLink onClick={onBack}>← Back</BackLink>
        <div className="mb-4">
          <SecondaryButton onClick={() => setZoomLevel('overview')}>Zoom out</SecondaryButton>
        </div>
        <section data-testid="evidence-panel" className="rounded-lg border border-ink-200 bg-paper-raised px-4 py-3">
          <SectionLabel>Evidence</SectionLabel>
          <p className="text-sm text-ink-700">{selectedEvent.description}</p>
          <p className="font-mono text-xs text-ink-500">{eventDate(selectedEvent.occurredAt)}</p>
        </section>
      </PageShell>
    )
  }

  const caughtUpWithNoActivity = catchUpSince && data.evolutionTimeline.length === 0

  return (
    <PageShell>
      <BackLink onClick={onBack}>← Back</BackLink>
      <PageHeading eyebrow="Context Rewind" title={data.entityName} />

      {catchUpSince && (
        <p className="mb-4 text-sm font-medium text-ink-900">
          {caughtUpWithNoActivity
            ? `Nothing has changed since ${catchUpSince}.`
            : `${data.evolutionTimeline.length} change${data.evolutionTimeline.length === 1 ? '' : 's'} since ${catchUpSince}`}
        </p>
      )}

      {!caughtUpWithNoActivity && (
        <section className="mb-8">
          <SectionLabel>Story of this code</SectionLabel>
          <ol className="flex flex-col gap-1.5">
            {data.evolutionTimeline.map((event, index) => (
              <li key={index}>
                <button
                  type="button"
                  data-testid="timeline-event"
                  onClick={() => setSelectedEvent(event)}
                  className="w-full rounded-lg px-3 py-2 text-left text-sm text-ink-700 hover:bg-ink-100"
                >
                  <span className="mr-2 font-mono text-xs text-ink-500">{eventDate(event.occurredAt)}</span>
                  {event.description}
                </button>
              </li>
            ))}
            <li className="px-3 py-2 text-sm font-medium text-ink-900">Current state</li>
          </ol>
        </section>
      )}

      {selectedEvent && (
        <section data-testid="evidence-panel" className="mb-8 rounded-lg border border-ink-200 bg-paper-raised px-4 py-3">
          <SectionLabel>Evidence</SectionLabel>
          <p className="text-sm text-ink-700">{selectedEvent.description}</p>
          <p className="mb-2 font-mono text-xs text-ink-500">{eventDate(selectedEvent.occurredAt)}</p>
          <PrimaryButton onClick={() => setZoomLevel('detail')}>Zoom in</PrimaryButton>
        </section>
      )}

      {data.pullRequestReferences.length > 0 && (
        <section className="mb-8">
          <SectionLabel>Pull Requests</SectionLabel>
          <ul className="flex flex-col gap-1">
            {data.pullRequestReferences.map((reference) => (
              <li key={reference.number} className="flex items-center gap-2">
                <a href={reference.url} target="_blank" rel="noreferrer" className="text-sm text-accent hover:underline">
                  Pull Request {reference.number}
                </a>
                <button
                  type="button"
                  onClick={() => setReviewingPr(reference)}
                  className="text-xs font-medium text-ink-500 hover:text-ink-900"
                >
                  View review
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {data.aiNarrative && (
        <section className="mb-8">
          <SectionLabel>Narrative</SectionLabel>
          <p className="mb-1.5 text-sm text-ink-700">{data.aiNarrative}</p>
          <span className="rounded-full bg-ink-100 px-2 py-0.5 text-xs font-medium text-ink-500">
            AI-generated interpretation
          </span>
        </section>
      )}

      <ContextLayers data={data} />
    </PageShell>
  )
}

/** A relationship view of the entity's module and its direct dependencies/dependents (ticket
 * #191), reusing the Semantic Canvas's own module topology data rather than a new class-level
 * dependency graph — Athena models relationships at module granularity only. Selecting a related
 * module returns to the canvas focused there via `onOpenModule`, not into another entity's own
 * Context Rewind (there's no class-level node to navigate a click into). */
function ContextMap({
  moduleName,
  onBack,
  onOpenModule,
}: {
  moduleName: string
  onBack: () => void
  onOpenModule?: (moduleName: string) => void
}) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['module-topology'],
    queryFn: getModuleTopology,
    retry: false,
  })

  if (isError) {
    return <ErrorState message="Could not load the Context Map." />
  }
  if (isLoading || !data) {
    return <LoadingState />
  }

  const dependsOn = data.dependencies.filter((dependency) => dependency.from === moduleName)
  const dependedOnBy = data.dependencies.filter((dependency) => dependency.to === moduleName)

  return (
    <PageShell>
      <BackLink onClick={onBack}>← Back</BackLink>
      <PageHeading eyebrow="Context Map" title={moduleName} />
      <section className="mb-8">
        <SectionLabel>Relationships</SectionLabel>
        <ul className="flex flex-col gap-1.5">
          {[...dependsOn, ...dependedOnBy].map((dependency) => (
            <li key={`${dependency.from}->${dependency.to}`} className="flex items-center gap-2 text-sm text-ink-700">
              <span>
                {dependency.from} depends on {dependency.to}
              </span>
              <button
                type="button"
                onClick={() => onOpenModule?.(dependency.from === moduleName ? dependency.to : dependency.from)}
                className="rounded-lg border border-ink-200 px-2 py-1 text-xs font-medium text-ink-700 hover:border-accent hover:bg-ink-100"
              >
                {dependency.from === moduleName ? dependency.to : dependency.from}
              </button>
            </li>
          ))}
          {dependsOn.length === 0 && dependedOnBy.length === 0 && (
            <li className="text-sm text-ink-500">No known relationships for this module.</li>
          )}
        </ul>
      </section>
    </PageShell>
  )
}

/** A Pull Request's existing review comments and reviewer verdicts (ticket #192), shown as a
 * flat, file-grouped list — not a structured reasoning trail. See the ticket for why: the real
 * review data has no reply-threading, timestamps, or diff position to build one from. */
function PullRequestReviewView({ reference, onBack }: { reference: PullRequestEvidence; onBack: () => void }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['pull-request-review', reference.repositoryFullName, reference.number],
    queryFn: () => getPullRequestReview(reference.repositoryFullName, reference.number),
    retry: false,
  })

  if (isError) {
    return <ErrorState message="Could not load this Pull Request's review." />
  }
  if (isLoading || !data) {
    return <LoadingState />
  }

  const commentsByPath = new Map<string, typeof data.comments>()
  for (const comment of data.comments) {
    commentsByPath.set(comment.path, [...(commentsByPath.get(comment.path) ?? []), comment])
  }

  const nothingRecorded = data.comments.length === 0 && data.reviews.length === 0

  return (
    <PageShell>
      <BackLink onClick={onBack}>← Back</BackLink>
      <PageHeading eyebrow="Review" title={`Pull Request ${reference.number}`} />

      {nothingRecorded ? (
        <p className="text-sm text-ink-500">Nothing was recorded for this review.</p>
      ) : (
        <>
          {data.reviews.length > 0 && (
            <section className="mb-8">
              <SectionLabel>Verdicts</SectionLabel>
              <ul className="flex flex-col gap-1">
                {data.reviews.map((review, index) => (
                  <li key={index} className="text-sm text-ink-700">
                    {review.reviewer}: {review.state}
                  </li>
                ))}
              </ul>
            </section>
          )}

          {[...commentsByPath.entries()].map(([path, comments]) => (
            <section key={path} className="mb-8">
              <SectionLabel>{path}</SectionLabel>
              <ul className="flex flex-col gap-2">
                {comments.map((comment, index) => (
                  <li key={index} className="rounded-lg border border-ink-200 bg-paper-raised px-3 py-2">
                    <p className="mb-1 text-xs font-semibold text-ink-900">{comment.author}</p>
                    <p className="text-sm text-ink-700">{comment.body}</p>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </>
      )}
    </PageShell>
  )
}

/** Separates different kinds of context visually — Current, Evolution, Discussions, Decisions,
 * Knowledge (ticket #190) — instead of mixing them into one AI-generated narrative. Behaves as
 * progressively explorable layers, not tabs: more than one can be expanded at once. Current,
 * Discussions, and Decisions always report nothing recorded — Athena has no structured source for
 * "what the code does today," "what developers discussed," or "what the team decided" yet (see
 * the ticket for why); Evolution and Knowledge are backed by real data. */
function ContextLayers({ data }: { data: ContextRewind }) {
  const [expandedLayers, setExpandedLayers] = useState<Set<string>>(new Set())

  function toggle(name: string) {
    setExpandedLayers((current) => {
      const next = new Set(current)
      if (next.has(name)) {
        next.delete(name)
      } else {
        next.add(name)
      }
      return next
    })
  }

  const nothingRecorded = <p className="text-sm text-ink-500">Nothing recorded yet.</p>

  const layers: { name: string; content: React.ReactNode }[] = [
    { name: 'Current', content: nothingRecorded },
    {
      name: 'Evolution',
      content:
        data.evolutionTimeline.length > 0 ? (
          <p className="text-sm text-ink-700">
            {data.evolutionTimeline.length} recorded change{data.evolutionTimeline.length === 1 ? '' : 's'} — see the
            timeline above.
          </p>
        ) : (
          nothingRecorded
        ),
    },
    { name: 'Discussions', content: nothingRecorded },
    { name: 'Decisions', content: nothingRecorded },
    {
      name: 'Knowledge',
      content:
        data.knowledgeFacts.length > 0 ? (
          <ul className="flex flex-col gap-1">
            {data.knowledgeFacts.map((fact, index) => (
              <li key={index} className="text-sm text-ink-700">
                {fact}
              </li>
            ))}
          </ul>
        ) : (
          nothingRecorded
        ),
    },
  ]

  return (
    <section className="mb-8">
      <SectionLabel>Context Layers</SectionLabel>
      <div className="flex flex-col gap-2">
        {layers.map((layer) => (
          <div key={layer.name} className="rounded-lg border border-ink-200">
            <button
              type="button"
              aria-expanded={expandedLayers.has(layer.name)}
              onClick={() => toggle(layer.name)}
              className="w-full px-3 py-2 text-left text-sm font-medium text-ink-900 hover:bg-ink-100"
            >
              {layer.name}
            </button>
            {expandedLayers.has(layer.name) && <div className="border-t border-ink-200 px-3 py-2">{layer.content}</div>}
          </div>
        ))}
      </div>
    </section>
  )
}

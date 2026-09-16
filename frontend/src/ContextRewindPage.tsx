import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getContextRewind, NoPullRequestSelectedError, type TimelineEvent } from './api'
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

/** Context Rewind's foundational story timeline & evidence panel (ticket #187), reached through
 * a semantic-zoom progression (ticket #188) so the amount of information increases as the
 * developer zooms in rather than presenting everything at once. */
export default function ContextRewindPage({
  entityName,
  onBack,
  moduleName,
  conceptName,
}: {
  entityName: string
  onBack: () => void
  /** Where this entity sits, shown at the Orientation level (ticket #188) — both optional since
   * not every Context Rewind entry point can supply them yet; Orientation falls back to just the
   * entity name when absent. */
  moduleName?: string
  conceptName?: string
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

  if (zoomLevel === 'orientation') {
    const breadcrumb = moduleName && conceptName ? `${moduleName} › ${conceptName} › ${entityName}` : entityName
    return (
      <PageShell>
        <BackLink onClick={onBack}>← Back</BackLink>
        <PageHeading eyebrow="Context Rewind" title={breadcrumb} />
        <div className="mb-6">
          <PrimaryButton onClick={() => setZoomLevel('overview')}>Zoom in</PrimaryButton>
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
              <li key={reference.number}>
                <a href={reference.url} target="_blank" rel="noreferrer" className="text-sm text-accent hover:underline">
                  Pull Request {reference.number}
                </a>
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
    </PageShell>
  )
}

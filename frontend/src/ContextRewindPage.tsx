import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getContextRewind, NoPullRequestSelectedError, type TimelineEvent } from './api'
import { BackLink, ErrorState, LoadingState, PageHeading, PageShell, SectionLabel } from './ui'

/** The date an event happened, formatted deterministically (no locale-dependent wording) for
 * both the timeline and the evidence panel. */
function eventDate(occurredAt: string): string {
  return occurredAt.slice(0, 10)
}

/** Context Rewind's foundational story timeline & evidence panel (ticket #187): a curated
 * timeline of an entity's history, ending at its current state, with Pull Request references
 * and an (optional, clearly-labeled) AI-generated narrative alongside it. */
export default function ContextRewindPage({ entityName, onBack }: { entityName: string; onBack: () => void }) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['context-rewind', entityName],
    queryFn: () => getContextRewind(entityName),
    retry: false,
  })
  const [selectedEvent, setSelectedEvent] = useState<TimelineEvent | undefined>(undefined)

  if (isError) {
    if (error instanceof NoPullRequestSelectedError) {
      return <ErrorState message="Select a pull request first to use Context Rewind." />
    }
    return <ErrorState message={`Could not load Context Rewind for ${entityName}.`} />
  }

  if (isLoading || !data) {
    return <LoadingState />
  }

  return (
    <PageShell>
      <BackLink onClick={onBack}>← Back</BackLink>
      <PageHeading eyebrow="Context Rewind" title={data.entityName} />

      {data.insufficientHistoryMessage ? (
        <p className="text-sm text-ink-500">{data.insufficientHistoryMessage}</p>
      ) : (
        <>
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

          {selectedEvent && (
            <section data-testid="evidence-panel" className="mb-8 rounded-lg border border-ink-200 bg-paper-raised px-4 py-3">
              <SectionLabel>Evidence</SectionLabel>
              <p className="text-sm text-ink-700">{selectedEvent.description}</p>
              <p className="font-mono text-xs text-ink-500">{eventDate(selectedEvent.occurredAt)}</p>
            </section>
          )}

          {data.pullRequestReferences.length > 0 && (
            <section className="mb-8">
              <SectionLabel>Pull Requests</SectionLabel>
              <ul className="flex flex-col gap-1">
                {data.pullRequestReferences.map((reference) => (
                  <li key={reference.number}>
                    <a
                      href={reference.url}
                      target="_blank"
                      rel="noreferrer"
                      className="text-sm text-accent hover:underline"
                    >
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
        </>
      )}
    </PageShell>
  )
}

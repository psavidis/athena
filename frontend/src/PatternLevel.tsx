import type { SemanticDimensionEntry } from './api'
import { Card, LevelConfidenceChip, SectionLabel } from './ui'

/**
 * The Pattern level's hero card(s) (ticket #96 §"Pattern level"): the
 * recognizable implementation technique(s) a Change embodies, each with
 * the structural changes that support it listed underneath and linked
 * back to the Structure level. Selecting a card itself (ticket #99 §10)
 * drives cross-highlighting of any other classification sharing its
 * evidence.
 *
 * The supporting structural changes are wrapped in a "structural cluster"
 * group (ticket #101 §15/#91 §15) that animates in with a slight rise, so
 * entering the Pattern level reads as those small structural elements
 * converging into the named pattern rather than a static list appearing.
 */
export default function PatternLevel({
  entries,
  onSelectSupporting,
  onSelectConcept,
  onHoverConcept,
}: {
  entries: SemanticDimensionEntry[]
  onSelectSupporting: (conceptName: string) => void
  onSelectConcept?: (entry: SemanticDimensionEntry) => void
  onHoverConcept?: (conceptName: string | undefined) => void
}) {
  return (
    <section>
      <SectionLabel>Pattern</SectionLabel>
      {entries.length === 0 ? (
        <Card className="p-5">
          <p className="text-sm text-ink-500">No Pattern classification for this Change yet.</p>
        </Card>
      ) : (
        <div className="space-y-4">
          {entries.map((entry) => (
            <Card
              key={entry.conceptName}
              className={'border-l-4 border-l-lv-pattern p-5' + (onSelectConcept ? ' cursor-pointer' : '')}
              onClick={onSelectConcept ? () => onSelectConcept(entry) : undefined}
              role={onSelectConcept ? 'button' : undefined}
              tabIndex={onSelectConcept ? 0 : undefined}
              onKeyDown={
                onSelectConcept
                  ? (e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        onSelectConcept(entry)
                      }
                    }
                  : undefined
              }
            >
              <div className="mb-2 flex items-center gap-2">
                <h3
                  className="text-lg font-semibold text-ink-900"
                  onMouseEnter={onHoverConcept ? () => onHoverConcept(entry.conceptName) : undefined}
                  onMouseLeave={onHoverConcept ? () => onHoverConcept(undefined) : undefined}
                >
                  {entry.conceptName}
                </h3>
                <LevelConfidenceChip dimension="PATTERN" inferred={entry.inferred} confidencePercent={entry.confidencePercent} />
              </div>
              <p className="text-sm text-ink-700">{entry.conceptDescription}</p>
              {entry.supportingConceptNames.length > 0 && (
                <div role="group" aria-label={`${entry.conceptName} structural cluster`}>
                  <ul aria-label={`${entry.conceptName} supporting structural changes`} className="mt-3 space-y-1">
                    {entry.supportingConceptNames.map((conceptName, i) => (
                      <li key={conceptName} className="animate-cluster-in" style={{ animationDelay: `${i * 40}ms` }}>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation()
                            onSelectSupporting(conceptName)
                          }}
                          className="rounded px-1 text-sm text-accent underline decoration-accent/40 decoration-2 underline-offset-4 hover:bg-accent-soft"
                        >
                          {conceptName}
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}
    </section>
  )
}

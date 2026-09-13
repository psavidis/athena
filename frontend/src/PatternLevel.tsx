import type { SemanticDimensionEntry } from './api'
import { Card, SectionLabel } from './ui'

/**
 * The Pattern level's hero card(s) (ticket #96 §"Pattern level"): the
 * recognizable implementation technique(s) a Change embodies, each with
 * the structural changes that support it listed underneath and linked
 * back to the Structure level. Selecting a card itself (ticket #99 §10)
 * drives cross-highlighting of any other classification sharing its
 * evidence.
 */
export default function PatternLevel({
  entries,
  onSelectSupporting,
  onSelectConcept,
}: {
  entries: SemanticDimensionEntry[]
  onSelectSupporting: (conceptName: string) => void
  onSelectConcept?: (entry: SemanticDimensionEntry) => void
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
              className={'p-5' + (onSelectConcept ? ' cursor-pointer' : '')}
              onClick={onSelectConcept ? () => onSelectConcept(entry) : undefined}
            >
              <div className="mb-2 flex items-center gap-2">
                <h3 className="text-lg font-semibold text-ink-900">{entry.conceptName}</h3>
                <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800">
                  {entry.inferred ? 'Inferred' : 'Observed'} · {entry.confidencePercent}%
                </span>
              </div>
              <p className="text-sm text-ink-700">{entry.conceptDescription}</p>
              {entry.supportingConceptNames.length > 0 && (
                <ul aria-label={`${entry.conceptName} supporting structural changes`} className="mt-3 space-y-1">
                  {entry.supportingConceptNames.map((conceptName) => (
                    <li key={conceptName}>
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
              )}
            </Card>
          ))}
        </div>
      )}
    </section>
  )
}

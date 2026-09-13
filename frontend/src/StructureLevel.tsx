import type { SemanticDimensionEntry } from './api'
import { Card, SectionLabel } from './ui'

/**
 * The Structure level's chip grid (ticket #96 §"Structure level"): one
 * compact, terse chip per atomic structural change, deliberately
 * lightweight so it doesn't compete visually with the higher semantic
 * levels. Selecting a chip is how a reviewer picks which one's diff
 * evidence gets highlighted in the evidence panel.
 */
export default function StructureLevel({
  entries,
  selectedConceptName,
  onSelect,
}: {
  entries: SemanticDimensionEntry[]
  selectedConceptName: string | undefined
  onSelect: (conceptName: string) => void
}) {
  return (
    <section>
      <SectionLabel>Structure</SectionLabel>
      {entries.length === 0 ? (
        <Card className="p-5">
          <p className="text-sm text-ink-500">No Structure classification for this Change yet.</p>
        </Card>
      ) : (
        <ul aria-label="Structural changes" className="flex flex-wrap gap-2">
          {entries.map((entry) => (
            <li key={entry.conceptName}>
              <button
                type="button"
                aria-current={entry.conceptName === selectedConceptName ? 'true' : undefined}
                onClick={() => onSelect(entry.conceptName)}
                className={
                  'rounded-full border px-3 py-1.5 text-sm font-medium transition-colors ' +
                  (entry.conceptName === selectedConceptName
                    ? 'border-ink-900 bg-ink-900 text-white'
                    : 'border-ink-200 bg-paper-raised text-ink-700 hover:border-ink-300 hover:bg-ink-100')
                }
              >
                {entry.conceptName}
              </button>
              <span className="ml-2 inline-flex items-center rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-800">
                {entry.inferred ? 'Inferred' : 'Observed'} · {entry.confidencePercent}%
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

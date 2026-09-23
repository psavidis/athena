import type { SemanticDimensionEntry } from './api'
import { Card, LevelConfidenceChip, SectionLabel } from './ui'

/**
 * The Structure level's chip grid (ticket #96 §"Structure level"): one
 * compact, terse chip per atomic structural change, deliberately
 * lightweight so it doesn't compete visually with the higher semantic
 * levels. Selecting a chip is how a reviewer picks which one's diff
 * evidence gets highlighted in the evidence panel. A grouped entry (ticket
 * #287: all test changes of one test class) shows how many changes it folds.
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
                    ? 'border-lv-structure bg-lv-structure text-white'
                    : 'border-ink-200 bg-paper-raised text-ink-700 hover:border-lv-structure/40 hover:bg-lv-structure-soft')
                }
              >
                {entry.conceptName}
              </button>
              {!!entry.groupedMoveCount && entry.groupedMoveCount > 0 && (
                <span className="ml-2 inline-block rounded-full bg-lv-structure-soft px-2 py-0.5 align-middle text-xs font-semibold text-ink-700">
                  {entry.groupedMoveCount} {entry.groupedMoveCount === 1 ? 'change' : 'changes'}
                </span>
              )}
              <span className="ml-2 inline-block align-middle">
                <LevelConfidenceChip dimension="STRUCTURAL" inferred={entry.inferred} confidencePercent={entry.confidencePercent} />
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

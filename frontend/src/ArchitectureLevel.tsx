import type { SemanticDimensionEntry } from './api'
import { Card, LevelConfidenceChip, SectionLabel } from './ui'

/**
 * The Architecture level's layered roles stack (ticket #98
 * §"Architecture level"): the architectural role(s) the Change actually
 * touches, each shown highlighted — per the ticket's own Technical
 * Requirements ("Architecture renders only the roles present in the
 * classification"), not a full always-present stack of every recognized
 * role, since {@code ArchitectureTaxonomyClassifier} only ever produces a
 * classification for the role(s) a Change actually touches.
 *
 * Each role is also individually selectable (ticket #101: "selecting an
 * architectural role highlights its connected components"), reporting the
 * selected entry via {@code onSelectConcept} so the Explorer can drive its
 * usual shared-evidence cross-highlighting — the same mechanism connecting
 * Pattern to Framework (ticket #99 §10).
 */
export default function ArchitectureLevel({
  entries,
  onSelectConcept,
}: {
  entries: SemanticDimensionEntry[]
  onSelectConcept?: (entry: SemanticDimensionEntry) => void
}) {
  return (
    <section>
      <SectionLabel>Architecture</SectionLabel>
      {entries.length === 0 ? (
        <Card className="p-5">
          <p className="text-sm text-ink-500">No Architecture classification for this Change yet.</p>
        </Card>
      ) : (
        <ul aria-label="Architectural roles" className="space-y-2">
          {entries.map((entry) => (
            <li
              key={entry.conceptName}
              aria-current="true"
              className="rounded-xl border border-lv-architecture bg-lv-architecture-soft p-3"
            >
              <div className="flex items-center gap-2">
                {onSelectConcept ? (
                  <button
                    type="button"
                    onClick={() => onSelectConcept(entry)}
                    className="rounded text-sm font-medium text-lv-architecture underline decoration-lv-architecture/40 decoration-2 underline-offset-4 hover:bg-lv-architecture-soft"
                  >
                    {entry.conceptName}
                  </button>
                ) : (
                  <span className="text-sm font-medium text-lv-architecture">{entry.conceptName}</span>
                )}
                <LevelConfidenceChip dimension="ARCHITECTURE" inferred={true} confidencePercent={entry.confidencePercent} />
              </div>
              <p className="mt-1 text-xs text-ink-700">{entry.conceptDescription}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

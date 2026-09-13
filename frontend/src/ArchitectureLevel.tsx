import type { SemanticDimensionEntry } from './api'
import { Card, SectionLabel } from './ui'

/**
 * The Architecture level's layered roles stack (ticket #98
 * §"Architecture level"): the architectural role(s) the Change actually
 * touches, each shown highlighted — per the ticket's own Technical
 * Requirements ("Architecture renders only the roles present in the
 * classification"), not a full always-present stack of every recognized
 * role, since {@code ArchitectureTaxonomyClassifier} only ever produces a
 * classification for the role(s) a Change actually touches.
 */
export default function ArchitectureLevel({ entries }: { entries: SemanticDimensionEntry[] }) {
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
              className="rounded-xl border border-ink-900 bg-paper-raised p-3"
            >
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-ink-900">{entry.conceptName}</span>
                <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800">
                  Inferred · {entry.confidencePercent}%
                </span>
              </div>
              <p className="mt-1 text-xs text-ink-700">{entry.conceptDescription}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

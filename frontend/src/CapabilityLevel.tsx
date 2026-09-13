import type { SemanticDimensionEntry } from './api'
import { Card, LevelConfidenceChip, SectionLabel } from './ui'

/**
 * The Capability level's linked cards grid (ticket #97 §"Capability level"):
 * the meaningful business responsibility/capability a Change affects, in
 * human-readable terms. A Change touching more than one capability shows
 * one card per capability, each individually selectable to show its
 * supporting code evidence in the evidence panel.
 */
export default function CapabilityLevel({
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
      <SectionLabel>Capability</SectionLabel>
      {entries.length === 0 ? (
        <Card className="p-5">
          <p className="text-sm text-ink-500">No Capability classification for this Change yet.</p>
        </Card>
      ) : (
        <ul aria-label="Capabilities" className="space-y-3">
          {entries.map((entry) => (
            <li key={entry.conceptName}>
              <button
                type="button"
                aria-current={entry.conceptName === selectedConceptName ? 'true' : undefined}
                onClick={() => onSelect(entry.conceptName)}
                className={
                  'w-full rounded-xl border p-4 text-left transition-colors ' +
                  (entry.conceptName === selectedConceptName
                    ? 'border-lv-capability bg-lv-capability-soft'
                    : 'border-ink-200 bg-paper-raised hover:border-lv-capability/40')
                }
              >
                <div className="mb-1 flex items-center gap-2">
                  <h3 className="text-base font-semibold text-ink-900">{entry.conceptName}</h3>
                  <LevelConfidenceChip dimension="RESPONSIBILITY" inferred={true} confidencePercent={entry.confidencePercent} />
                </div>
                <p className="text-sm text-ink-700">{entry.conceptDescription}</p>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

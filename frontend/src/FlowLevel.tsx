import type { SemanticDimensionEntry } from './api'
import { Card, SectionLabel } from './ui'

/**
 * The Flow level's affected-flow panel (ticket #98 §"Flow level"): which
 * use-case flow a Change belongs to, always Observed. Shows the single
 * flow FlowTaxonomyClassifier correlates a Change against — no ordered
 * step-sequence data exists yet (unlike ticket #91's illustrative mockup
 * example), so this renders exactly what the classifier produces.
 */
export default function FlowLevel({ entries }: { entries: SemanticDimensionEntry[] }) {
  return (
    <section>
      <SectionLabel>Flow</SectionLabel>
      {entries.length === 0 ? (
        <Card className="p-5">
          <p className="text-sm text-ink-500">No Flow classification for this Change yet.</p>
        </Card>
      ) : (
        <div className="space-y-4">
          {entries.map((entry) => (
            <Card key={entry.conceptName} className="p-5">
              <div className="mb-2 flex items-center gap-2">
                <h3 className="text-lg font-semibold text-ink-900">{entry.conceptName}</h3>
                <span className="inline-flex items-center rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-800">
                  Observed · {entry.confidencePercent}%
                </span>
              </div>
              <p className="text-sm text-ink-700">{entry.conceptDescription}</p>
            </Card>
          ))}
        </div>
      )}
    </section>
  )
}

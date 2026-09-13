import type { SemanticDimensionEntry } from './api'
import { Card, DiffView, SectionLabel } from './ui'

/**
 * The Framework level's mechanism panel (ticket #97 §"Framework level"): the
 * framework/platform mechanism a Change embodies, always shown as Observed.
 * When the classification represents a mechanism transition (e.g. Spring
 * field injection -> constructor injection), the literal before/after
 * snippets are shown explicitly rather than just naming the mechanism.
 */
export default function FrameworkLevel({ entries }: { entries: SemanticDimensionEntry[] }) {
  return (
    <section>
      <SectionLabel>Framework</SectionLabel>
      {entries.length === 0 ? (
        <Card className="p-5">
          <p className="text-sm text-ink-500">No Framework classification for this Change yet.</p>
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
              <p className="mb-3 text-sm text-ink-700">{entry.conceptDescription}</p>
              {(entry.beforeEvidenceCount ?? 0) > 0 ? (
                <div className="grid gap-3 sm:grid-cols-2">
                  <div>
                    <p className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-500">Before</p>
                    {entry.evidence.slice(0, entry.beforeEvidenceCount).map((diff, i) => (
                      <DiffView key={i} diff={diff} />
                    ))}
                  </div>
                  <div>
                    <p className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-500">After</p>
                    {entry.evidence.slice(entry.beforeEvidenceCount).map((diff, i) => (
                      <DiffView key={i} diff={diff} />
                    ))}
                  </div>
                </div>
              ) : (
                entry.evidence.map((diff, i) => <DiffView key={i} diff={diff} />)
              )}
            </Card>
          ))}
        </div>
      )}
    </section>
  )
}

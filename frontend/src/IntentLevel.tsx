import type { SemanticDimensionEntry } from './api'
import { Card, SectionLabel } from './ui'

/**
 * The Intent level's "Why?" panel (ticket #99 §"Intent level"): the
 * primary inferred reason a Change was made, with the evidence backing
 * it, and any lower-confidence alternative readings shown distinctly so
 * Intent never reads as more certain than it is. `entries` are already
 * ranked primary-first by {@code IntentTaxonomyClassifier}/{@code
 * SemanticProfileController} — the first entry is the primary reading,
 * the rest are alternatives.
 */
export default function IntentLevel({ entries }: { entries: SemanticDimensionEntry[] }) {
  const [primary, ...alternatives] = entries

  return (
    <section>
      <SectionLabel>Intent</SectionLabel>
      {!primary ? (
        <Card className="p-5">
          <p className="text-sm text-ink-500">No Intent classification for this Change yet.</p>
        </Card>
      ) : (
        <div className="space-y-4">
          <Card className="p-5">
            <p className="mb-1 text-xs font-medium uppercase tracking-wide text-ink-500">Why?</p>
            <div className="mb-2 flex items-center gap-2">
              <h3 className="text-lg font-semibold text-ink-900">{primary.conceptName}</h3>
              <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800">
                Inferred · {primary.confidencePercent}%
              </span>
            </div>
            <p className="mb-3 text-sm text-ink-700">{primary.conceptDescription}</p>
            {primary.evidence.length > 0 && (
              <ul aria-label={`${primary.conceptName} supporting evidence`} className="list-disc space-y-1 pl-5">
                {primary.evidence.map((diff, i) => (
                  <li key={i} className="text-sm text-ink-700">
                    {diff}
                  </li>
                ))}
              </ul>
            )}
          </Card>

          {alternatives.length > 0 && (
            <div>
              <p className="mb-2 text-xs font-medium uppercase tracking-wide text-ink-500">Other possible readings</p>
              <ul aria-label="Alternative readings" className="flex flex-wrap gap-2">
                {alternatives.map((alternative) => (
                  <li
                    key={alternative.conceptName}
                    className="inline-flex items-center gap-1.5 rounded-full border border-ink-200 bg-paper-raised px-3 py-1.5 text-sm text-ink-700"
                  >
                    {alternative.conceptName}
                    <span className="text-xs text-ink-500">· {alternative.confidencePercent}%</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

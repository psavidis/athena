import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getSemanticProfile, type SemanticDimension, type SemanticDimensionEntry } from './api'
import { BackLink, Card, DiffView, ErrorState, LoadingState, SectionLabel } from './ui'

/**
 * The seven-level semantic spine (ticket #91 §2/§12), in Structure → Intent
 * order — the same order used to lay out the spine, walk the Change Story,
 * and default the initially-selected level. `label` is the UI-facing name;
 * `dimension` is the {@link SemanticDimension} it corresponds to on the
 * Change's Semantic Profile (ticket #94).
 */
const LEVELS: { label: string; dimension: SemanticDimension }[] = [
  { label: 'Structure', dimension: 'STRUCTURAL' },
  { label: 'Pattern', dimension: 'PATTERN' },
  { label: 'Framework', dimension: 'FRAMEWORK' },
  { label: 'Capability', dimension: 'RESPONSIBILITY' },
  { label: 'Flow', dimension: 'FEATURE' },
  { label: 'Architecture', dimension: 'ARCHITECTURE' },
  { label: 'Intent', dimension: 'INTENT' },
]

export default function SemanticChangeExplorerPage({
  changeKey,
  onBack,
}: {
  changeKey: string
  onBack: () => void
}) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['semantic-profile', changeKey],
    queryFn: () => getSemanticProfile(changeKey),
    retry: false,
  })
  const [currentDimension, setCurrentDimension] = useState<SemanticDimension>('STRUCTURAL')

  if (isError) {
    return <ErrorState message="Could not load this Change's Semantic Profile." />
  }
  if (isLoading || !data) {
    return <LoadingState />
  }

  // This ticket's job is the shell's mechanics — spine, Change Story, evidence
  // wiring — not each level's own visualization (separate tickets fill those
  // in), so a level with a classification renders a minimal generic view of
  // it here, and one without renders a plain empty state rather than
  // fabricating content (mirrors SemanticProfile's own "absent means
  // unclassified" rule).
  const currentEntry = data.dimensions.find((entry) => entry.dimension === currentDimension)
  const currentLabel = LEVELS.find((level) => level.dimension === currentDimension)!.label

  return (
    <div className="mx-auto max-w-6xl px-6 py-10 sm:px-10 sm:py-14">
      <div className="animate-rise-in">
        <BackLink onClick={onBack}>← Back to Change Map</BackLink>

        <ChangeStory dimensions={data.dimensions} onSelect={setCurrentDimension} />

        <div className="grid gap-6 lg:grid-cols-[14rem_1fr_1fr]">
          <Spine current={currentDimension} onSelect={setCurrentDimension} />
          <CenterStage label={currentLabel} entry={currentEntry} />
          <EvidencePanel label={currentLabel} entry={currentEntry} />
        </div>
      </div>
    </div>
  )
}

function Spine({
  current,
  onSelect,
}: {
  current: SemanticDimension
  onSelect: (dimension: SemanticDimension) => void
}) {
  return (
    <nav aria-label="Semantic levels">
      <SectionLabel>Semantic Spine</SectionLabel>
      <ul className="space-y-1">
        {LEVELS.map((level) => (
          <li key={level.dimension}>
            <button
              type="button"
              aria-current={level.dimension === current ? 'true' : undefined}
              onClick={() => onSelect(level.dimension)}
              className={
                'w-full rounded-lg px-3 py-2 text-left text-sm font-medium transition-colors ' +
                (level.dimension === current ? 'bg-ink-900 text-white' : 'text-ink-700 hover:bg-ink-100')
              }
            >
              {level.label}
            </button>
          </li>
        ))}
      </ul>
    </nav>
  )
}

function CenterStage({ label, entry }: { label: string; entry: SemanticDimensionEntry | undefined }) {
  return (
    <section>
      <SectionLabel>{label}</SectionLabel>
      <Card className="p-5">
        {entry ? (
          <>
            <div className="mb-2 flex items-center gap-2">
              <h2 className="text-lg font-semibold text-ink-900">{entry.conceptName}</h2>
              <ConfidenceChip entry={entry} />
            </div>
            <p className="text-sm text-ink-700">{entry.conceptDescription}</p>
          </>
        ) : (
          <p className="text-sm text-ink-500">No {label} classification for this Change yet.</p>
        )}
      </Card>
    </section>
  )
}

function ConfidenceChip({ entry }: { entry: SemanticDimensionEntry }) {
  return (
    <span
      className={
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ' +
        (entry.inferred ? 'bg-amber-50 text-amber-800' : 'bg-emerald-50 text-emerald-800')
      }
    >
      {entry.inferred ? 'Inferred' : 'Observed'} · {entry.confidencePercent}%
    </span>
  )
}

function EvidencePanel({ label, entry }: { label: string; entry: SemanticDimensionEntry | undefined }) {
  return (
    <section>
      <SectionLabel>Evidence</SectionLabel>
      {entry && entry.evidence.length > 0 ? (
        <div className="space-y-3">
          {entry.evidence.map((diff, i) => (
            <DiffView key={i} diff={diff} />
          ))}
        </div>
      ) : (
        <p className="text-sm text-ink-500">No evidence available for the {label} level.</p>
      )}
    </section>
  )
}

/**
 * The interactive sentence bridging the diff to its meaning (ticket #91
 * §11). This ticket's scope is the click-to-select mechanic and reflecting
 * the Change's real classifications — not the fully-templated narrative
 * prose from #91's own example (which needs data this Semantic Profile
 * doesn't carry, like the enclosing type name), so classified concepts are
 * joined plainly, in spine order.
 */
function ChangeStory({
  dimensions,
  onSelect,
}: {
  dimensions: SemanticDimensionEntry[]
  onSelect: (dimension: SemanticDimension) => void
}) {
  const byDimension = new Map(dimensions.map((entry) => [entry.dimension, entry]))
  // Structure is the raw evidence layer ("the diff is the evidence"), not part of the
  // narrated story — #91 §11's own example sentence starts at Pattern, never Structure.
  const ordered = LEVELS.filter((level) => level.dimension !== 'STRUCTURAL')
    .map((level) => byDimension.get(level.dimension))
    .filter((entry): entry is SemanticDimensionEntry => entry !== undefined)

  if (ordered.length === 0) {
    return null
  }

  return (
    <p className="mb-8 font-serif text-2xl leading-relaxed text-ink-900">
      {ordered.map((entry, i) => (
        <span key={entry.dimension}>
          {i > 0 && <span className="text-ink-300"> → </span>}
          <button
            type="button"
            onClick={() => onSelect(entry.dimension)}
            className="rounded px-0.5 underline decoration-accent/40 decoration-2 underline-offset-4 transition-colors hover:bg-accent-soft hover:text-accent"
          >
            {entry.conceptName}
          </button>
        </span>
      ))}
    </p>
  )
}

import { useQuery } from '@tanstack/react-query'
import { getReviewBriefing, type BriefingItem } from './reviewBriefing'
import { PrimaryButton } from './ui'

function BriefingSection({ title, items }: { title: string; items: BriefingItem[] }) {
  if (items.length === 0) return null
  return (
    <div>
      <h3 className="mb-1 text-xs font-semibold tracking-wide text-ink-500 uppercase">{title}</h3>
      <ul className="space-y-1">
        {items.map((item, index) => (
          <li key={index} className="text-sm text-ink-700">
            {item.description}
          </li>
        ))}
      </ul>
    </div>
  )
}

/**
 * Review Briefing's overlay over the Semantic Canvas (ticket #223): a
 * compact panel shown on first entering a PR, with the canvas visible
 * behind it (reuses Context Rewind's PR-integration overlay pattern,
 * #194 — a full-cover sibling within the canvas page, never a page-level
 * navigation away). Collapses to a small indicator on "Start Review";
 * reopenable at any time. `SemanticCanvasPage` owns the open/collapsed
 * state itself (per-PR, reset when the selected PR changes) — this
 * component only renders whichever state it's told.
 */
export default function ReviewBriefingOverlay({
  pullRequestNumber,
  collapsed,
  onStartReview,
  onReopen,
}: {
  pullRequestNumber: number
  collapsed: boolean
  onStartReview: () => void
  onReopen: () => void
}) {
  // Scoped to the PR (matching ContextRewindPage's own pull-request-review key precedent):
  // SemanticCanvasPage doesn't remount this component on a PR switch — only briefingCollapsed
  // resets — so an unscoped key would let react-query serve the previous PR's cached briefing
  // instead of fetching the new one's.
  const { data, isLoading, isError } = useQuery({
    queryKey: ['review-briefing', pullRequestNumber],
    queryFn: getReviewBriefing,
    retry: false,
  })

  if (collapsed) {
    return (
      <button
        type="button"
        onClick={onReopen}
        className="absolute top-4 right-4 z-40 rounded-full border border-canvas-line-strong bg-canvas-paper-raised px-3 py-1.5 text-xs font-semibold text-canvas-ink-soft shadow hover:border-canvas-gold"
      >
        Briefing
      </button>
    )
  }

  return (
    <div className="absolute inset-0 z-50 flex items-start justify-center bg-canvas-paper/60 backdrop-blur-sm">
      <div className="mt-16 w-full max-w-xl rounded-lg border border-canvas-line-strong bg-canvas-paper-raised p-6 shadow-lg">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-canvas-ink">Review Briefing</h2>
        </div>

        {isLoading && (
          <p role="status" aria-label="Loading Review Briefing" className="text-sm text-ink-500">
            Loading…
          </p>
        )}

        {isError && (
          <p role="alert" className="text-sm text-ink-500">
            This Review Briefing isn't available yet.
          </p>
        )}

        {data && (
          <div className="space-y-4">
            {data.changeSummary && <p className="text-sm text-ink-700">{data.changeSummary.description}</p>}
            <BriefingSection title="Focus areas" items={data.focusAreas} />
            <BriefingSection title="Uncertainties" items={data.uncertainties} />
            <BriefingSection title="Questions" items={data.questions} />
            <BriefingSection title="Historical context" items={data.historicalContext} />
            <BriefingSection title="Relevant knowledge" items={data.relevantKnowledge} />
          </div>
        )}

        <div className="mt-6 flex justify-end">
          <PrimaryButton onClick={onStartReview}>Start Review</PrimaryButton>
        </div>
      </div>
    </div>
  )
}

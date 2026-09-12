import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getChangeMap,
  setReviewState,
  NoPullRequestSelectedError,
  NotConnectedError,
  type ChangeCategory,
  type ChangeMapEntry,
  type ReviewState,
} from './api'

const CATEGORY_LABELS: Record<ChangeCategory, string> = {
  BEHAVIORAL: 'Behavioral',
  STRUCTURAL: 'Structural',
  MECHANICAL: 'Mechanical',
  UNKNOWN: 'Unknown',
}

const REVIEW_STATE_LABELS: Record<ChangeMapEntry['reviewState'], string> = {
  UNSEEN: 'Unseen',
  UNDERSTANDING: 'Understanding',
  REVIEWED: 'Reviewed',
  CONCERN: 'Concern',
  SKIPPED: 'Skipped',
}

export default function ChangeMapPage({
  onNotConnected,
  onNoPullRequestSelected,
  onSelectChange,
  onOpenPreSubmissionSummary,
}: {
  onNotConnected: () => void
  onNoPullRequestSelected: () => void
  onSelectChange: (changeKey: string) => void
  onOpenPreSubmissionSummary: () => void
}) {
  const queryClient = useQueryClient()
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['change-map'],
    queryFn: getChangeMap,
    retry: false,
  })

  const reviewStateMutation = useMutation({
    mutationFn: ({ changeKey, state }: { changeKey: string; state: ReviewState }) => setReviewState(changeKey, state),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['change-map'] }),
  })

  if (isError) {
    if (error instanceof NotConnectedError) {
      onNotConnected()
      return null
    }
    if (error instanceof NoPullRequestSelectedError) {
      onNoPullRequestSelected()
      return null
    }
    return <p className="mx-auto max-w-2xl px-4 py-12 text-red-600">Could not load the Change Map.</p>
  }

  if (isLoading || !data) {
    return <p className="mx-auto max-w-2xl px-4 py-12 text-neutral-500">Loading…</p>
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <div className="mb-6 flex items-start justify-between">
        <PrUnderstandingSummary prTitle={data.prTitle} categoryCounts={data.categoryCounts} />
        <button
          className="rounded bg-neutral-900 px-3 py-1.5 text-sm text-white"
          onClick={onOpenPreSubmissionSummary}
        >
          Review summary
        </button>
      </div>
      <ChangeMapList
        changes={data.changes}
        onSelectChange={onSelectChange}
        onSetReviewState={(changeKey, state) => reviewStateMutation.mutate({ changeKey, state })}
      />
    </div>
  )
}

function PrUnderstandingSummary({
  prTitle,
  categoryCounts,
}: {
  prTitle: string
  categoryCounts: Record<ChangeCategory, number>
}) {
  return (
    <section className="mb-8">
      <h1 className="mb-4 text-2xl font-semibold text-neutral-900">{prTitle}</h1>
      <dl className="flex gap-6">
        {(Object.keys(CATEGORY_LABELS) as ChangeCategory[]).map((category) => (
          <div key={category}>
            <dt className="text-sm text-neutral-500">{CATEGORY_LABELS[category]}</dt>
            <dd className="text-xl font-medium text-neutral-900">{categoryCounts[category]}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function ChangeMapList({
  changes,
  onSelectChange,
  onSetReviewState,
}: {
  changes: ChangeMapEntry[]
  onSelectChange: (changeKey: string) => void
  onSetReviewState: (changeKey: string, state: ReviewState) => void
}) {
  if (changes.length === 0) {
    return <p className="text-neutral-500">No Changes detected.</p>
  }

  return (
    <ul className="divide-y divide-neutral-200 rounded border border-neutral-200">
      {changes.map((change) => (
        <li key={change.id} className="flex items-center justify-between px-4 py-3 hover:bg-neutral-100">
          <button className="flex-1 text-left" onClick={() => onSelectChange(change.changeKey)}>
            <p className="text-neutral-900">{change.description}</p>
            <p className="text-sm text-neutral-500">{CATEGORY_LABELS[change.category]}</p>
          </button>
          <label className="sr-only" htmlFor={`review-state-${change.id}`}>
            Review state for {change.description}
          </label>
          <select
            id={`review-state-${change.id}`}
            className="rounded-full border border-neutral-200 bg-neutral-100 px-3 py-1 text-sm text-neutral-600"
            value={change.reviewState}
            onClick={(e) => e.stopPropagation()}
            onChange={(e) => onSetReviewState(change.changeKey, e.target.value as ReviewState)}
          >
            {(Object.keys(REVIEW_STATE_LABELS) as ReviewState[]).map((state) => (
              <option key={state} value={state}>
                {REVIEW_STATE_LABELS[state]}
              </option>
            ))}
          </select>
        </li>
      ))}
    </ul>
  )
}

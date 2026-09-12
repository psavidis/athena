import { useQuery } from '@tanstack/react-query'
import {
  getChangeMap,
  NoPullRequestSelectedError,
  NotConnectedError,
  type ChangeCategory,
  type ChangeMapEntry,
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
}: {
  onNotConnected: () => void
  onNoPullRequestSelected: () => void
  onSelectChange: (changeKey: string) => void
}) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['change-map'],
    queryFn: getChangeMap,
    retry: false,
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
      <PrUnderstandingSummary prTitle={data.prTitle} categoryCounts={data.categoryCounts} />
      <ChangeMapList changes={data.changes} onSelectChange={onSelectChange} />
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
}: {
  changes: ChangeMapEntry[]
  onSelectChange: (changeKey: string) => void
}) {
  if (changes.length === 0) {
    return <p className="text-neutral-500">No Changes detected.</p>
  }

  return (
    <ul className="divide-y divide-neutral-200 rounded border border-neutral-200">
      {changes.map((change) => (
        <li key={change.id}>
          <button
            className="flex w-full items-center justify-between px-4 py-3 text-left hover:bg-neutral-100"
            onClick={() => onSelectChange(change.changeKey)}
          >
            <div>
              <p className="text-neutral-900">{change.description}</p>
              <p className="text-sm text-neutral-500">{CATEGORY_LABELS[change.category]}</p>
            </div>
            <span className="rounded-full bg-neutral-100 px-3 py-1 text-sm text-neutral-600">
              {REVIEW_STATE_LABELS[change.reviewState]}
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}

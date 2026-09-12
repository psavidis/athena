import { useMutation, useQuery } from '@tanstack/react-query'
import { getPreSubmissionSummary, submitReview } from './api'

const ACTION_LABELS = {
  APPROVE: 'Approve',
  REQUEST_CHANGES: 'Request changes',
}

export default function PreSubmissionSummaryPage({ onBack }: { onBack: () => void }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['pre-submission-summary'],
    queryFn: getPreSubmissionSummary,
    retry: false,
  })

  const submitMutation = useMutation({
    mutationFn: submitReview,
  })

  if (isError) {
    return <p className="mx-auto max-w-2xl px-4 py-12 text-red-600">Could not load the pre-submission summary.</p>
  }

  if (isLoading || !data) {
    return <p className="mx-auto max-w-2xl px-4 py-12 text-neutral-500">Loading…</p>
  }

  if (submitMutation.isSuccess) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-12">
        <p className="text-neutral-900">Your review was submitted to GitHub.</p>
        <button className="mt-4 text-sm text-neutral-500 hover:underline" onClick={onBack}>
          ← Back to Change Map
        </button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <button className="mb-4 text-sm text-neutral-500 hover:underline" onClick={onBack}>
        ← Back to Change Map
      </button>

      <h1 className="mb-6 text-2xl font-semibold text-neutral-900">Pre-submission summary</h1>

      <Section title="Reviewed">
        <ChangeTitleList titles={data.reviewedChangeTitles} />
      </Section>
      <Section title="Mechanical">
        <ChangeTitleList titles={data.mechanicalChangeTitles} />
      </Section>
      <Section title="Concerns">
        <ChangeTitleList titles={data.concernChangeTitles} />
      </Section>

      <p className="mb-6 text-sm text-neutral-600">Comments: {data.commentCount}</p>

      <p className="mb-6 text-sm text-neutral-600">
        GitHub action: <span className="font-medium text-neutral-900">{ACTION_LABELS[data.gitHubAction]}</span>
      </p>

      <button
        className="rounded bg-neutral-900 px-4 py-2 text-white disabled:opacity-40"
        disabled={submitMutation.isPending}
        onClick={() => submitMutation.mutate()}
      >
        {submitMutation.isPending ? 'Submitting…' : 'Confirm and submit'}
      </button>
      {submitMutation.isError && (
        <p className="mt-3 text-sm text-red-600">Could not submit — please try again.</p>
      )}
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-6">
      <h2 className="mb-2 text-sm font-medium text-neutral-500">{title}</h2>
      {children}
    </section>
  )
}

function ChangeTitleList({ titles }: { titles: string[] }) {
  if (titles.length === 0) {
    return <p className="text-sm text-neutral-400">None</p>
  }
  return (
    <ul className="text-sm text-neutral-700">
      {titles.map((title) => (
        <li key={title}>{title}</li>
      ))}
    </ul>
  )
}

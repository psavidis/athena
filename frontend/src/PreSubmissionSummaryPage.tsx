import { useMutation, useQuery } from '@tanstack/react-query'
import { getPreSubmissionSummary, submitReview } from './api'
import { BackLink, Card, ErrorState, LoadingState, PageShell, PrimaryButton, SectionLabel } from './ui'

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
    return <ErrorState message="Could not load the pre-submission summary." />
  }

  if (isLoading || !data) {
    return <LoadingState />
  }

  if (submitMutation.isSuccess) {
    return (
      <PageShell narrow>
        <Card className="px-6 py-8 text-center">
          <p className="mb-4 text-sm text-ink-900">Your review was submitted to GitHub.</p>
          <BackLink onClick={onBack}>← Back to Change Map</BackLink>
        </Card>
      </PageShell>
    )
  }

  return (
    <PageShell narrow>
      <BackLink onClick={onBack}>← Back to Change Map</BackLink>

      <div className="mb-8">
        <p className="mb-1.5 text-xs font-medium tracking-wide text-ink-500 uppercase">Judgment</p>
        <h1 className="text-2xl font-semibold text-ink-900">Pre-submission summary</h1>
      </div>

      <Section title="Reviewed">
        <ChangeTitleList titles={data.reviewedChangeTitles} />
      </Section>
      <Section title="Mechanical">
        <ChangeTitleList titles={data.mechanicalChangeTitles} />
      </Section>
      <Section title="Concerns">
        <ChangeTitleList titles={data.concernChangeTitles} />
      </Section>

      <p className="mb-3 text-sm text-ink-600">Comments: {data.commentCount}</p>

      <p className="mb-8 text-sm text-ink-600">
        GitHub action: <span className="font-medium text-ink-900">{ACTION_LABELS[data.gitHubAction]}</span>
      </p>

      <PrimaryButton disabled={submitMutation.isPending} onClick={() => submitMutation.mutate()}>
        {submitMutation.isPending ? 'Submitting…' : 'Confirm and submit'}
      </PrimaryButton>
      {submitMutation.isError && (
        <p className="mt-3 text-sm text-red-600">Could not submit — please try again.</p>
      )}
    </PageShell>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-6">
      <SectionLabel>{title}</SectionLabel>
      {children}
    </section>
  )
}

function ChangeTitleList({ titles }: { titles: string[] }) {
  if (titles.length === 0) {
    return <p className="text-sm text-ink-300">None</p>
  }
  return (
    <Card className="divide-y divide-ink-200 overflow-hidden">
      {titles.map((title) => (
        <p key={title} className="px-4 py-2.5 text-sm text-ink-700">
          {title}
        </p>
      ))}
    </Card>
  )
}

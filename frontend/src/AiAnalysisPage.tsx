import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  acceptFinding,
  AiAnalysisNotConfiguredError,
  dismissFinding,
  getAiContextBoundary,
  triggerAiAnalysis,
  type AiFinding,
} from './api'
import { BackLink, Card, ErrorState, LoadingState, PageShell, PrimaryButton, SecondaryButton, SectionLabel } from './ui'

export default function AiAnalysisPage({
  onBack,
  onSelectChange,
}: {
  onBack: () => void
  onSelectChange: (changeKey: string) => void
}) {
  const { data: boundary, isLoading, isError } = useQuery({
    queryKey: ['ai-context-boundary'],
    queryFn: getAiContextBoundary,
    retry: false,
  })

  const [findings, setFindings] = useState<AiFinding[] | null>(null)

  const analysisMutation = useMutation({
    mutationFn: triggerAiAnalysis,
    onSuccess: setFindings,
  })

  const acceptMutation = useMutation({
    mutationFn: acceptFinding,
    onSuccess: setFindings,
  })

  const dismissMutation = useMutation({
    mutationFn: dismissFinding,
    onSuccess: setFindings,
  })

  if (isError) {
    return <ErrorState message="Could not load the AI context boundary." />
  }

  if (isLoading || !boundary) {
    return <LoadingState />
  }

  return (
    <PageShell narrow>
      <BackLink onClick={onBack}>← Back to Change Map</BackLink>

      <div className="mb-8">
        <p className="mb-1.5 text-xs font-medium tracking-wide text-ink-500 uppercase">Assisted review</p>
        <h1 className="text-2xl font-semibold text-ink-900">AI analysis</h1>
      </div>

      <Section title="Included">
        <ChangeTitleList titles={boundary.includedChangeTitles} empty="No Changes to include." />
      </Section>
      <Section title="Excluded — not yet reviewed">
        <ChangeTitleList titles={boundary.excludedUnreviewedChangeTitles} empty="None" />
      </Section>
      <Section title="Excluded — generated files">
        <ChangeTitleList titles={boundary.excludedGeneratedChangeTitles} empty="None" />
      </Section>
      {boundary.privateNotesExcluded && (
        <p className="mb-6 text-sm text-ink-500">Private notes are never sent.</p>
      )}

      {!findings && (
        <PrimaryButton disabled={analysisMutation.isPending} onClick={() => analysisMutation.mutate()}>
          {analysisMutation.isPending ? 'Analyzing…' : 'Trigger AI analysis'}
        </PrimaryButton>
      )}
      {analysisMutation.isError && (
        <p className="mt-3 text-sm text-red-600">
          {analysisMutation.error instanceof AiAnalysisNotConfiguredError
            ? 'AI analysis is not configured — set ANTHROPIC_API_KEY on the backend and restart Athena.'
            : 'Could not trigger AI analysis — please try again.'}
        </p>
      )}

      {findings && (
        <FindingsList
          findings={findings}
          onAccept={(id) => acceptMutation.mutate(id)}
          onDismiss={(id) => dismissMutation.mutate(id)}
          onSelectChange={onSelectChange}
        />
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

function ChangeTitleList({ titles, empty }: { titles: string[]; empty: string }) {
  if (titles.length === 0) {
    return <p className="text-sm text-ink-300">{empty}</p>
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

const DISPOSITION_META: Record<AiFinding['disposition'], { label: string; className: string }> = {
  PENDING: { label: 'Pending', className: 'bg-ink-100 text-ink-600' },
  ACCEPTED: { label: 'Accepted', className: 'bg-emerald-50 text-emerald-800 ring-1 ring-inset ring-emerald-200' },
  DISMISSED: { label: 'Dismissed', className: 'bg-ink-100 text-ink-400' },
}

function FindingsList({
  findings,
  onAccept,
  onDismiss,
  onSelectChange,
}: {
  findings: AiFinding[]
  onAccept: (id: string) => void
  onDismiss: (id: string) => void
  onSelectChange: (changeKey: string) => void
}) {
  if (findings.length === 0) {
    return <p className="animate-rise-in mt-6 text-sm text-ink-500">No findings.</p>
  }
  return (
    <Card className="animate-rise-in mt-6 divide-y divide-ink-200 overflow-hidden">
      {findings.map((finding) => {
        const disposition = DISPOSITION_META[finding.disposition]
        return (
          <div key={finding.id} className="px-4 py-3.5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm text-ink-900">{finding.description}</p>
                {finding.jumpTargetChangeKey !== null && (
                  <button
                    className="mt-1 text-sm text-accent hover:underline"
                    onClick={() => onSelectChange(finding.jumpTargetChangeKey as string)}
                  >
                    Jump to Change →
                  </button>
                )}
              </div>
              <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${disposition.className}`}>
                {disposition.label}
              </span>
            </div>
            {finding.disposition === 'PENDING' && (
              <div className="mt-2 flex gap-2">
                <SecondaryButton onClick={() => onAccept(finding.id)}>Accept</SecondaryButton>
                <SecondaryButton onClick={() => onDismiss(finding.id)}>Dismiss</SecondaryButton>
              </div>
            )}
          </div>
        )
      })}
    </Card>
  )
}

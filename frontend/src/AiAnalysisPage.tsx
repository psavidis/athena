import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  acceptFinding,
  dismissFinding,
  getAiContextBoundary,
  triggerAiAnalysis,
  type AiFinding,
} from './api'

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
    return <p className="mx-auto max-w-2xl px-4 py-12 text-red-600">Could not load the AI context boundary.</p>
  }

  if (isLoading || !boundary) {
    return <p className="mx-auto max-w-2xl px-4 py-12 text-neutral-500">Loading…</p>
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <button className="mb-4 text-sm text-neutral-500 hover:underline" onClick={onBack}>
        ← Back to Change Map
      </button>

      <h1 className="mb-6 text-2xl font-semibold text-neutral-900">AI analysis</h1>

      <Section title="Included">
        <ChangeTitleList titles={boundary.includedChangeTitles} empty="No Changes to include." />
      </Section>
      <Section title="Excluded — not yet reviewed">
        <ChangeTitleList titles={boundary.excludedUnreviewedChangeTitles} empty="None" />
      </Section>
      <Section title="Excluded — generated files">
        <ChangeTitleList titles={boundary.excludedGeneratedChangeTitles} empty="None" />
      </Section>
      <p className="mb-6 text-sm text-neutral-500">
        {boundary.privateNotesExcluded ? 'Private notes are never sent.' : ''}
      </p>

      {!findings && (
        <button
          className="rounded bg-neutral-900 px-4 py-2 text-white disabled:opacity-40"
          disabled={analysisMutation.isPending}
          onClick={() => analysisMutation.mutate()}
        >
          {analysisMutation.isPending ? 'Analyzing…' : 'Trigger AI analysis'}
        </button>
      )}
      {analysisMutation.isError && (
        <p className="mt-3 text-sm text-red-600">Could not trigger AI analysis — please try again.</p>
      )}

      {findings && (
        <FindingsList
          findings={findings}
          onAccept={(id) => acceptMutation.mutate(id)}
          onDismiss={(id) => dismissMutation.mutate(id)}
          onSelectChange={onSelectChange}
        />
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

function ChangeTitleList({ titles, empty }: { titles: string[]; empty: string }) {
  if (titles.length === 0) {
    return <p className="text-sm text-neutral-400">{empty}</p>
  }
  return (
    <ul className="text-sm text-neutral-700">
      {titles.map((title) => (
        <li key={title}>{title}</li>
      ))}
    </ul>
  )
}

const DISPOSITION_LABELS = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  DISMISSED: 'Dismissed',
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
    return <p className="text-neutral-500">No findings.</p>
  }
  return (
    <ul className="divide-y divide-neutral-200 rounded border border-neutral-200">
      {findings.map((finding) => (
        <li key={finding.id} className="px-4 py-3">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-neutral-900">{finding.description}</p>
              {finding.jumpTargetChangeKey !== null && (
                <button
                  className="mt-1 text-sm text-neutral-500 hover:underline"
                  onClick={() => onSelectChange(finding.jumpTargetChangeKey as string)}
                >
                  Jump to Change →
                </button>
              )}
            </div>
            <span className="shrink-0 rounded-full bg-neutral-100 px-3 py-1 text-sm text-neutral-600">
              {DISPOSITION_LABELS[finding.disposition]}
            </span>
          </div>
          {finding.disposition === 'PENDING' && (
            <div className="mt-2 flex gap-2">
              <button
                className="rounded border border-neutral-300 px-3 py-1 text-sm text-neutral-700 hover:bg-neutral-100"
                onClick={() => onAccept(finding.id)}
              >
                Accept
              </button>
              <button
                className="rounded border border-neutral-300 px-3 py-1 text-sm text-neutral-700 hover:bg-neutral-100"
                onClick={() => onDismiss(finding.id)}
              >
                Dismiss
              </button>
            </div>
          )}
        </li>
      ))}
    </ul>
  )
}

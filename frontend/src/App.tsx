import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getGitHubConnectUrl, getGitHubStatus, listOpenPullRequests, listRepositories, selectPullRequest } from './api'
import type { ImportedPullRequest } from './api'
import ChangeDetailPage from './ChangeDetailPage'
import SemanticCanvasPage from './SemanticCanvasPage'
import PreSubmissionSummaryPage from './PreSubmissionSummaryPage'
import AiAnalysisPage from './AiAnalysisPage'
import { BackLink, Card, ErrorState, LoadingState, PageHeading, PageShell, PrimaryButton } from './ui'

export default function App() {
  const [connected, setConnected] = useState<boolean | null>(null)
  const [selectedRepo, setSelectedRepo] = useState<string | null>(null)
  const [selectedPr, setSelectedPr] = useState<ImportedPullRequest | null>(null)
  // The Semantic Canvas is the landing view for a PR the instant it's
  // selected (ticket #129, replacing the retired Explorer's #91 landing
  // behavior) — no separate category/change-list screen before it.
  // The Diff view overlay (ticket #100): shows one Change's raw diff,
  // reached from AI Analysis; exiting it returns to the Canvas.
  const [diffViewChangeKey, setDiffViewChangeKey] = useState<string | null>(null)
  const [showingSummary, setShowingSummary] = useState(false)
  const [showingAiAnalysis, setShowingAiAnalysis] = useState(false)

  useEffect(() => {
    getGitHubStatus()
      .then((status) => setConnected(status.connected))
      .catch(() => setConnected(false))
  }, [])

  function selectPr(pr: ImportedPullRequest) {
    setSelectedPr(pr)
  }

  function renderContent() {
    if (connected === null) {
      return null
    }
    if (!connected) {
      return <ConnectStep />
    }
    if (selectedPr) {
      if (diffViewChangeKey) {
        return <ChangeDetailPage changeKey={diffViewChangeKey} onBack={() => setDiffViewChangeKey(null)} />
      }
      if (showingSummary) {
        return <PreSubmissionSummaryPage onBack={() => setShowingSummary(false)} />
      }
      if (showingAiAnalysis) {
        return (
          <AiAnalysisPage
            onBack={() => setShowingAiAnalysis(false)}
            onSelectChange={(changeKey) => {
              setShowingAiAnalysis(false)
              setDiffViewChangeKey(changeKey)
            }}
          />
        )
      }
      return (
        <SemanticCanvasPage
          pullRequest={selectedPr}
          onNotConnected={() => {
            setConnected(false)
            setSelectedRepo(null)
            setSelectedPr(null)
          }}
          onNoPullRequestSelected={() => setSelectedPr(null)}
        />
      )
    }
    if (selectedRepo) {
      return (
        <PullRequestStep repositoryFullName={selectedRepo} onBack={() => setSelectedRepo(null)} onSelected={selectPr} />
      )
    }
    return <RepositoryStep onSelected={setSelectedRepo} />
  }

  return (
    <div className="flex min-h-screen flex-col">
      {!selectedPr && <AppHeader />}
      <div className="flex-1">{renderContent()}</div>
    </div>
  )
}

/**
 * The Athena logo, shown at the top of every pre-PR page (ticket #109) so
 * the product has a consistent, recognizable identity before a PR is open.
 * Once a PR is selected, SemanticChangeExplorerPage's own topbar (matching
 * ticket #91's approved mockup) replaces this — there's exactly one top bar
 * on screen at a time, never both stacked.
 */
function AppHeader() {
  return (
    <header className="flex items-center gap-2 border-b border-ink-200 bg-paper-raised px-6 py-3.5 sm:px-10">
      <img src="/athena-logo.png" alt="Athena" className="h-8 w-8 rounded-full" />
      <span className="font-display text-base font-medium tracking-tight text-ink-900">Athena</span>
    </header>
  )
}

function ConnectStep() {
  const mutation = useMutation({
    mutationFn: getGitHubConnectUrl,
    onSuccess: ({ url }) => {
      window.location.href = url
    },
  })

  return (
    <PageShell>
      <div className="max-w-md">
        <PageHeading eyebrow="Step 1 of 3" title="Connect to GitHub" />
        <p className="mb-6 text-sm leading-relaxed text-ink-700">
          Athena reviews Pull Requests by installing a GitHub App on the account or organization that
          owns the repositories you want to review. You'll pick exactly which repositories to grant
          access to on GitHub.
        </p>
        <PrimaryButton disabled={mutation.isPending} onClick={() => mutation.mutate()}>
          {mutation.isPending ? 'Redirecting…' : 'Connect GitHub'}
        </PrimaryButton>
        {mutation.isError && (
          <p className="mt-3 text-sm text-red-600">{(mutation.error as Error).message}</p>
        )}
      </div>
    </PageShell>
  )
}

function RepositoryStep({ onSelected }: { onSelected: (repositoryFullName: string) => void }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['repositories'],
    queryFn: listRepositories,
  })

  if (isError) {
    return <ErrorState message="Could not load repositories." />
  }
  if (isLoading) {
    return <LoadingState />
  }

  return (
    <PageShell>
      <div className="max-w-xl">
        <PageHeading eyebrow="Step 2 of 3" title="Select a repository" />
        <Card className="divide-y divide-ink-200 overflow-hidden">
          {data?.map((repo) => (
            <button
              key={repo.fullName}
              className="block w-full px-4 py-3 text-left text-sm text-ink-900 transition-colors hover:bg-ink-100"
              onClick={() => onSelected(repo.fullName)}
            >
              {repo.fullName}
            </button>
          ))}
        </Card>
      </div>
    </PageShell>
  )
}

function PullRequestStep({
  repositoryFullName,
  onBack,
  onSelected,
}: {
  repositoryFullName: string
  onBack: () => void
  onSelected: (pr: ImportedPullRequest) => void
}) {
  const queryClient = useQueryClient()
  const { data, isLoading, isError } = useQuery({
    queryKey: ['pulls', repositoryFullName],
    queryFn: () => listOpenPullRequests(repositoryFullName),
  })
  const mutation = useMutation({
    mutationFn: (number: number) => selectPullRequest(repositoryFullName, number),
    onSuccess: onSelected,
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['pulls', repositoryFullName] }),
  })

  return (
    <PageShell>
      <div className="max-w-xl">
        <BackLink onClick={onBack}>← Back to repositories</BackLink>
        <PageHeading eyebrow="Step 3 of 3" title={`Open PRs — ${repositoryFullName}`} />
        {isError && <p className="text-sm text-red-600">Could not load pull requests.</p>}
        {isLoading && <LoadingState />}
        {data?.length === 0 && <p className="text-sm text-ink-500">No open pull requests.</p>}
        {data && data.length > 0 && (
          <Card className="divide-y divide-ink-200 overflow-hidden">
            {data.map((pr) => (
              <button
                key={pr.number}
                className="block w-full px-4 py-3 text-left text-sm text-ink-900 transition-colors hover:bg-ink-100 disabled:cursor-not-allowed disabled:opacity-40"
                disabled={mutation.isPending}
                onClick={() => mutation.mutate(pr.number)}
              >
                <span className="text-ink-500">#{pr.number}</span> {pr.title}
              </button>
            ))}
          </Card>
        )}
        {mutation.isError && (
          <p className="mt-3 text-sm text-red-600">{(mutation.error as Error).message}</p>
        )}
      </div>
    </PageShell>
  )
}

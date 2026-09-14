import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getGitHubStatus, listOpenPullRequests, listRepositories, selectPullRequest } from './api'
import type { ImportedPullRequest } from './api'
import ChangeDetailPage from './ChangeDetailPage'
import SemanticCanvasPage from './SemanticCanvasPage'
import PreSubmissionSummaryPage from './PreSubmissionSummaryPage'
import AiAnalysisPage from './AiAnalysisPage'
import GitHubAccessPage from './GitHubAccessPage'
import { AthenaTopBar, BackLink, Card, ErrorState, LoadingState, PageHeading, PageShell, PrimaryButton } from './ui'

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
  // GitHub Access is its own page (ticket #113/#145), reachable from the
  // main page rather than a blocking gate the whole app sits behind.
  const [showingGitHubAccess, setShowingGitHubAccess] = useState(false)

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
    if (showingGitHubAccess) {
      return <GitHubAccessPage onBack={() => setShowingGitHubAccess(false)} />
    }
    if (!connected) {
      return <NotConnectedPrompt onOpenGitHubAccess={() => setShowingGitHubAccess(true)} />
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
    return <RepositoryStep onSelected={setSelectedRepo} onOpenGitHubAccess={() => setShowingGitHubAccess(true)} />
  }

  return (
    <div className="flex min-h-screen flex-col">
      {!selectedPr && !showingGitHubAccess && <AthenaTopBar />}
      <div className="flex-1">{renderContent()}</div>
    </div>
  )
}

/**
 * Shown on the main page instead of a blocking full-page gate (ticket
 * #113/#145) when GitHub isn't connected yet — a prompt directing the user
 * to the GitHub Access page, where the actual "Connect GitHub" action now
 * lives, rather than the main page owning the connect flow itself.
 */
function NotConnectedPrompt({ onOpenGitHubAccess }: { onOpenGitHubAccess: () => void }) {
  return (
    <PageShell>
      <div className="max-w-md">
        <PageHeading title="Not connected to GitHub" />
        <p className="mb-6 text-sm leading-relaxed text-ink-700">
          Athena needs a GitHub connection to browse repositories and Pull Requests.
        </p>
        <PrimaryButton onClick={onOpenGitHubAccess}>Go to GitHub Access</PrimaryButton>
      </div>
    </PageShell>
  )
}

function RepositoryStep({
  onSelected,
  onOpenGitHubAccess,
}: {
  onSelected: (repositoryFullName: string) => void
  onOpenGitHubAccess: () => void
}) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['repositories'],
    queryFn: listRepositories,
  })

  return (
    <PageShell>
      <div className="max-w-xl">
        <div className="flex items-start justify-between gap-4">
          <PageHeading title="Select a repository" />
          <button
            type="button"
            onClick={onOpenGitHubAccess}
            className="flex-shrink-0 text-sm text-accent hover:underline"
          >
            GitHub Access
          </button>
        </div>
        {isError && <ErrorState message="Could not load repositories." />}
        {isLoading && <LoadingState />}
        {data && (
          <Card className="divide-y divide-ink-200 overflow-hidden">
            {data.map((repo) => (
              <button
                key={repo.fullName}
                className="block w-full px-4 py-3 text-left text-sm text-ink-900 transition-colors hover:bg-ink-100"
                onClick={() => onSelected(repo.fullName)}
              >
                {repo.fullName}
              </button>
            ))}
          </Card>
        )}
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

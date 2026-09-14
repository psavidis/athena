import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createDiff, getGitHubStatus, listOpenPullRequests, listRepositories, selectPullRequest } from './api'
import type { ImportedPullRequest } from './api'
import ChangeDetailPage from './ChangeDetailPage'
import SemanticCanvasPage from './SemanticCanvasPage'
import PreSubmissionSummaryPage from './PreSubmissionSummaryPage'
import AiAnalysisPage from './AiAnalysisPage'
import GitHubAccessPage from './GitHubAccessPage'
import {
  AthenaTopBar,
  BackLink,
  Card,
  ErrorState,
  LoadingState,
  PageHeading,
  PageShell,
  PrimaryButton,
  SecondaryButton,
} from './ui'

export default function App() {
  const [connected, setConnected] = useState<boolean | null>(null)
  const [selectedRepo, setSelectedRepo] = useState<string | null>(null)
  const [selectedPr, setSelectedPr] = useState<ImportedPullRequest | null>(null)
  // A standalone Diff (ticket #111/#153): a local comparison with no GitHub
  // PR at all. Lands on the same Semantic Canvas a PR Review does —
  // SemanticCanvasPage already accepts pullRequest={null}, so no separate
  // Diff-shaped canvas view is needed, only this session-selection flag.
  const [diffActive, setDiffActive] = useState(false)
  const [showingDiffEntryPoint, setShowingDiffEntryPoint] = useState(false)
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
    if (showingDiffEntryPoint) {
      return (
        <DiffEntryPoint
          onBack={() => setShowingDiffEntryPoint(false)}
          onCreated={() => {
            setShowingDiffEntryPoint(false)
            setDiffActive(true)
          }}
        />
      )
    }
    if (diffActive) {
      return (
        <SemanticCanvasPage
          pullRequest={null}
          onNotConnected={() => {
            setConnected(false)
            setDiffActive(false)
          }}
          onNoPullRequestSelected={() => setDiffActive(false)}
        />
      )
    }
    if (!connected) {
      return (
        <NotConnectedPrompt
          onOpenGitHubAccess={() => setShowingGitHubAccess(true)}
          onStartDiff={() => setShowingDiffEntryPoint(true)}
        />
      )
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
    return (
      <RepositoryStep
        onSelected={setSelectedRepo}
        onOpenGitHubAccess={() => setShowingGitHubAccess(true)}
        onStartDiff={() => setShowingDiffEntryPoint(true)}
      />
    )
  }

  return (
    <div className="flex min-h-screen flex-col">
      {!selectedPr && !diffActive && !showingGitHubAccess && <AthenaTopBar />}
      <div className="flex-1">{renderContent()}</div>
    </div>
  )
}

/**
 * Shown on the main page instead of a blocking full-page gate (ticket
 * #113/#145) when GitHub isn't connected yet — a prompt directing the user
 * to the GitHub Access page, where the actual "Connect GitHub" action now
 * lives, rather than the main page owning the connect flow itself. Also
 * offers the Diff entry point (ticket #111/#153): comparing two local
 * revisions needs no GitHub connection at all.
 */
function NotConnectedPrompt({
  onOpenGitHubAccess,
  onStartDiff,
}: {
  onOpenGitHubAccess: () => void
  onStartDiff: () => void
}) {
  return (
    <PageShell>
      <div className="max-w-md">
        <PageHeading title="Not connected to GitHub" />
        <p className="mb-6 text-sm leading-relaxed text-ink-700">
          Athena needs a GitHub connection to browse repositories and Pull Requests.
        </p>
        <div className="flex flex-wrap gap-2.5">
          <PrimaryButton onClick={onOpenGitHubAccess}>Go to GitHub Access</PrimaryButton>
          <SecondaryButton onClick={onStartDiff}>Start a Diff</SecondaryButton>
        </div>
      </div>
    </PageShell>
  )
}

function RepositoryStep({
  onSelected,
  onOpenGitHubAccess,
  onStartDiff,
}: {
  onSelected: (repositoryFullName: string) => void
  onOpenGitHubAccess: () => void
  onStartDiff: () => void
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
          <div className="flex flex-shrink-0 items-center gap-4">
            <button type="button" onClick={onStartDiff} className="text-sm text-accent hover:underline">
              Start a Diff
            </button>
            <button type="button" onClick={onOpenGitHubAccess} className="text-sm text-accent hover:underline">
              GitHub Access
            </button>
          </div>
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

/**
 * Diff entry point (ticket #111/#153): comparing two revisions of a local
 * Git repository, no GitHub involved. Consumes the local-Diff endpoint from
 * #152 (`createDiff`); on success, the caller (App) switches to the same
 * Semantic Canvas a PR Review lands on.
 */
function DiffEntryPoint({ onBack, onCreated }: { onBack: () => void; onCreated: () => void }) {
  const [repositoryPath, setRepositoryPath] = useState('')
  const [baseRevision, setBaseRevision] = useState('')
  const [headRevision, setHeadRevision] = useState('')
  const mutation = useMutation({
    mutationFn: () => createDiff(repositoryPath, baseRevision, headRevision),
    onSuccess: onCreated,
  })

  return (
    <PageShell>
      <div className="max-w-md">
        <BackLink onClick={onBack}>← Back</BackLink>
        <PageHeading title="Start a Diff" />
        <p className="mb-6 text-sm leading-relaxed text-ink-700">
          Compare two revisions of a local Git repository — no GitHub connection needed.
        </p>
        <form
          className="space-y-3.5"
          onSubmit={(e) => {
            e.preventDefault()
            mutation.mutate()
          }}
        >
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-ink-500">Repository path</span>
            <input
              type="text"
              value={repositoryPath}
              onChange={(e) => setRepositoryPath(e.target.value)}
              placeholder="/path/to/repository"
              className="w-full rounded-lg border border-ink-200 bg-paper-raised px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent"
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-ink-500">Base revision</span>
            <input
              type="text"
              value={baseRevision}
              onChange={(e) => setBaseRevision(e.target.value)}
              placeholder="main"
              className="w-full rounded-lg border border-ink-200 bg-paper-raised px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent"
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-ink-500">Head revision</span>
            <input
              type="text"
              value={headRevision}
              onChange={(e) => setHeadRevision(e.target.value)}
              placeholder="feature-branch"
              className="w-full rounded-lg border border-ink-200 bg-paper-raised px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent"
            />
          </label>
          <PrimaryButton
            type="submit"
            disabled={!repositoryPath || !baseRevision || !headRevision || mutation.isPending}
          >
            Compare
          </PrimaryButton>
          {mutation.isError && <p className="text-sm text-red-600">Could not create Diff. Check the repository path and revisions.</p>}
        </form>
      </div>
    </PageShell>
  )
}

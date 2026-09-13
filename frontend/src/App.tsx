import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getGitHubConnectUrl, getGitHubStatus, listOpenPullRequests, listRepositories, selectPullRequest } from './api'
import type { ImportedPullRequest } from './api'
import ChangeMapPage from './ChangeMapPage'
import ChangeDetailPage from './ChangeDetailPage'
import SemanticChangeExplorerPage from './SemanticChangeExplorerPage'
import PreSubmissionSummaryPage from './PreSubmissionSummaryPage'
import AiAnalysisPage from './AiAnalysisPage'
import { BackLink, Card, ErrorState, LoadingState, PageHeading, PageShell, PrimaryButton, SecondaryButton } from './ui'

type ChangeViewMode = 'diff' | 'explorer'

export default function App() {
  const [connected, setConnected] = useState<boolean | null>(null)
  const [selectedRepo, setSelectedRepo] = useState<string | null>(null)
  const [selectedPr, setSelectedPr] = useState<ImportedPullRequest | null>(null)
  const [selectedChangeKey, setSelectedChangeKey] = useState<string | null>(null)
  // The module-scoped Semantic Change Explorer (ticket #122's follow-up):
  // selecting a module from the Change Map's module list opens the Explorer
  // directly, aggregated across every Change in that module, instead of
  // requiring a reviewer to expand the module's flat change list and pick
  // one Change first. Mutually exclusive with selectedChangeKey — opening
  // one clears the other, since they're two different Explorer scopes, not
  // states that stack.
  const [selectedModuleName, setSelectedModuleName] = useState<string | null>(null)
  // Which of the two reachable views a selected Change is shown in (ticket
  // #100): the traditional flat diff, or the Semantic Change Explorer.
  // #91 replaces the *primary* experience, not the diff itself — the diff
  // remains reachable as the underlying evidence (#91 §1). Reset whenever
  // a different Change is selected, not preserved as some global
  // preference, since the ticket only asks that switching mode not lose
  // the selected PR/Change. Not applicable to a module-scoped Explorer
  // (selectedModuleName): a module has no single diff to toggle to.
  const [changeViewMode, setChangeViewMode] = useState<ChangeViewMode>('diff')
  const [showingSummary, setShowingSummary] = useState(false)
  const [showingAiAnalysis, setShowingAiAnalysis] = useState(false)

  useEffect(() => {
    getGitHubStatus()
      .then((status) => setConnected(status.connected))
      .catch(() => setConnected(false))
  }, [])

  function renderContent() {
    if (connected === null) {
      return null
    }
    if (!connected) {
      return <ConnectStep />
    }
    if (selectedPr) {
      if (selectedModuleName) {
        return (
          <SemanticChangeExplorerPage
            scope={{ kind: 'module', moduleName: selectedModuleName }}
            onBack={() => setSelectedModuleName(null)}
          />
        )
      }
      if (selectedChangeKey) {
        const onBack = () => {
          setSelectedChangeKey(null)
          setChangeViewMode('diff')
        }
        return (
          <div>
            <ChangeViewModeToggle mode={changeViewMode} onChange={setChangeViewMode} />
            {changeViewMode === 'diff' ? (
              <ChangeDetailPage changeKey={selectedChangeKey} onBack={onBack} />
            ) : (
              <SemanticChangeExplorerPage scope={{ kind: 'change', changeKey: selectedChangeKey }} onBack={onBack} />
            )}
          </div>
        )
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
              setSelectedChangeKey(changeKey)
            }}
          />
        )
      }
      return (
        <ChangeMapPage
          onNotConnected={() => {
            setConnected(false)
            setSelectedRepo(null)
            setSelectedPr(null)
          }}
          onNoPullRequestSelected={() => setSelectedPr(null)}
          onSelectChange={setSelectedChangeKey}
          onSelectModule={setSelectedModuleName}
          onOpenPreSubmissionSummary={() => setShowingSummary(true)}
          onOpenAiAnalysis={() => setShowingAiAnalysis(true)}
        />
      )
    }
    if (selectedRepo) {
      return (
        <PullRequestStep
          repositoryFullName={selectedRepo}
          onBack={() => setSelectedRepo(null)}
          onSelected={setSelectedPr}
        />
      )
    }
    return <RepositoryStep onSelected={setSelectedRepo} />
  }

  return (
    <div className="flex min-h-screen flex-col">
      <AppHeader />
      <div className="flex-1">{renderContent()}</div>
    </div>
  )
}

/**
 * The Athena logo, shown once at the top of every page (ticket #109) so
 * the product has a consistent, recognizable identity instead of a
 * generic, unbranded shell. Full-width, bordered like the approved mockup's
 * `.topbar` (ticket #91's reference design) — no centered max-width column,
 * since nothing below it is centered either (see PageShell).
 */
function AppHeader() {
  return (
    <header className="flex items-center gap-2 border-b border-ink-200 bg-paper-raised px-6 py-3.5 sm:px-10">
      <img src="/athena-logo.png" alt="Athena" className="h-8 w-8 rounded-full" />
      <span className="font-display text-base font-medium tracking-tight text-ink-900">Athena</span>
    </header>
  )
}

/**
 * The top-bar Diff view / Semantic Explorer mode switch (ticket #100), kept
 * reachable alongside a selected Change so a reviewer can move between the
 * two without losing which Change/PR they're looking at (ticket #91 §1).
 */
function ChangeViewModeToggle({
  mode,
  onChange,
}: {
  mode: ChangeViewMode
  onChange: (mode: ChangeViewMode) => void
}) {
  return (
    <div className="flex justify-end gap-2 px-6 pt-6 sm:px-10">
      {mode === 'diff' ? (
        <SecondaryButton onClick={() => onChange('explorer')}>Semantic Explorer</SecondaryButton>
      ) : (
        <SecondaryButton onClick={() => onChange('diff')}>Diff view</SecondaryButton>
      )}
    </div>
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

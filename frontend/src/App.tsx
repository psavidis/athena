import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createDiff, getGitHubStatus, selectPullRequest } from './api'
import type { ImportedPullRequest } from './api'
import ChangeDetailPage from './ChangeDetailPage'
import LiveSessionCanvas from './LiveSessionCanvas'
import PreSubmissionSummaryPage from './PreSubmissionSummaryPage'
import AiAnalysisPage from './AiAnalysisPage'
import GitHubAccessPage from './GitHubAccessPage'
import KnowledgeSettingsPage from './KnowledgeSettingsPage'
import { AthenaTopBar, BackLink, PageHeading, PageShell, PrimaryButton, RepoPrPicker, SecondaryButton } from './ui'
import { parsePullRequestPath, pullRequestPath } from './shareUrl'
import { parseLiveSessionPath } from './liveSessionUrl'

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
  // Knowledge (Obsidian) settings is another optional External Systems /
  // Integrations entry (ticket #118), reachable alongside GitHub Access —
  // never a blocking gate, since Athena works fully with no Knowledge
  // Provider configured.
  const [showingKnowledgeSettings, setShowingKnowledgeSettings] = useState(false)
  // A shared Live Code Review Session link (/live/:id, ticket #158) opened
  // this app instance — read once, directly, as the initial state (see
  // liveSessionRoute's own render-branch comment below for why this has to
  // be checked ahead of the normal connected/selectedPr branching).
  const [liveSessionRoute, setLiveSessionRoute] = useState<string | null>(() =>
    parseLiveSessionPath(window.location.pathname),
  )

  useEffect(() => {
    getGitHubStatus()
      .then((status) => setConnected(status.connected))
      .catch(() => setConnected(false))
  }, [])

  const queryClient = useQueryClient()
  const selectPrMutation = useMutation({
    mutationFn: (pr: { repositoryFullName: string; number: number }) =>
      selectPullRequest(pr.repositoryFullName, pr.number),
    onSuccess: (pr, variables) => {
      setSelectedRepo(variables.repositoryFullName)
      setSelectedPr(pr)
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['pulls', selectedRepo] }),
  })
  const { mutate: selectPr } = selectPrMutation

  // A selected PR is addressable at /repositories/:owner/:repo/pulls/:number
  // (see shareUrl.ts) so the current browser URL is always a working share
  // link. On load, a shared link re-runs the same select flow the picker
  // uses, against the opening user's own GitHub session — repo selection
  // included, via the mutation's own onSuccess above.
  useEffect(() => {
    if (!connected) return
    const route = parsePullRequestPath(window.location.pathname)
    if (route) selectPr(route)
  }, [connected, selectPr])

  useEffect(() => {
    if (!selectedRepo || !selectedPr) return
    const path = pullRequestPath({ repositoryFullName: selectedRepo, number: selectedPr.number })
    if (window.location.pathname !== path) {
      window.history.replaceState(null, '', path)
    }
  }, [selectedRepo, selectedPr])

  function selectRepo(repositoryFullName: string) {
    setSelectedRepo(repositoryFullName)
    setSelectedPr(null)
  }

  // The repo/PR picker (ticket #128 follow-up) lives in the top bar on every
  // main-product screen instead of gating the app behind standalone
  // "Select a repository" / "Open PRs" screens — switching what you're
  // reviewing never has to leave the product surface.
  const picker = connected ? (
    <RepoPrPicker
      selectedRepo={selectedRepo}
      selectedPr={selectedPr}
      onSelectRepo={selectRepo}
      onSelectPr={(pr) => selectPrMutation.mutate({ repositoryFullName: selectedRepo!, number: pr.number })}
    />
  ) : undefined

  // SemanticCanvasPage renders its own AthenaTopBar (it needs to add its
  // review-mode/comments controls, and the picker, to the bar) — everywhere
  // else App renders the one shared bar. Without this check the two would
  // stack when a PR/Diff is active.
  const showingSemanticCanvas =
    diffActive || (selectedPr !== null && !diffViewChangeKey && !showingSummary && !showingAiAnalysis)

  function renderContent() {
    if (connected === null) {
      return null
    }
    // A shared Live Code Review Session link takes priority over the usual
    // connected/repo-picker branching below (ticket #158): joining must work
    // for a browser that has nothing selected yet — or isn't even connected
    // to GitHub yet — not only once a PR/Diff is already active. Once
    // LiveSessionCanvas learns the session's real PR identity from joining,
    // it calls back here to select that PR the normal way; `selectedPr`
    // populating clears this branch, so rendering falls through to the
    // ordinary selectedPr branch below (same LiveSessionCanvas instance,
    // now with a real `pullRequest`, not a second join). A standalone-Diff-
    // backed session never gets that callback, so a joiner stays on this
    // branch — a disclosed limitation: they get presence/comments, not the
    // canvas content itself, since there's no PR for them to independently
    // select (see the ticket's "PR-backed only" scope note).
    if (liveSessionRoute && !selectedPr) {
      return (
        <LiveSessionCanvas
          pullRequest={null}
          picker={picker}
          onNotConnected={() => setConnected(false)}
          onNoPullRequestSelected={() => {}}
          onJoinedPr={(repositoryFullName, number) => {
            setLiveSessionRoute(null)
            selectPr({ repositoryFullName, number })
          }}
        />
      )
    }
    if (showingGitHubAccess) {
      return <GitHubAccessPage onBack={() => setShowingGitHubAccess(false)} />
    }
    if (showingKnowledgeSettings) {
      return <KnowledgeSettingsPage onBack={() => setShowingKnowledgeSettings(false)} />
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
        <LiveSessionCanvas
          pullRequest={null}
          picker={picker}
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
          onOpenKnowledgeSettings={() => setShowingKnowledgeSettings(true)}
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
        <LiveSessionCanvas
          pullRequest={selectedPr}
          picker={picker}
          onNotConnected={() => {
            setConnected(false)
            setSelectedRepo(null)
            setSelectedPr(null)
          }}
          onNoPullRequestSelected={() => setSelectedPr(null)}
        />
      )
    }
    return (
      <EmptyCanvasShell
        picker={picker}
        onOpenGitHubAccess={() => setShowingGitHubAccess(true)}
        onOpenKnowledgeSettings={() => setShowingKnowledgeSettings(true)}
        onStartDiff={() => setShowingDiffEntryPoint(true)}
      />
    )
  }

  // Every screen owns its own AthenaTopBar now (the Semantic Canvas, the
  // EmptyCanvasShell) except this one, where GitHub isn't connected yet and
  // there's no picker to show — a bare shared bar keeps the brand chrome
  // present even here.
  const showingBareTopBar =
    !showingGitHubAccess && !showingKnowledgeSettings && !showingDiffEntryPoint && !showingSemanticCanvas
    && connected === false

  return (
    <div className="flex min-h-screen flex-col">
      {showingBareTopBar && <AthenaTopBar />}
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
  onOpenKnowledgeSettings,
  onStartDiff,
}: {
  onOpenGitHubAccess: () => void
  onOpenKnowledgeSettings: () => void
  onStartDiff: () => void
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-6 px-6 py-24 text-center">
      <h1 className="text-2xl font-semibold text-ink-900">Not connected to GitHub</h1>
      <p className="max-w-md text-sm leading-relaxed text-ink-700">
        Athena needs a GitHub connection to browse repositories and Pull Requests.
      </p>
      <div className="flex flex-wrap justify-center gap-2.5">
        <PrimaryButton onClick={onOpenGitHubAccess}>Go to GitHub Access</PrimaryButton>
        <SecondaryButton onClick={onStartDiff}>Start a Diff</SecondaryButton>
      </div>
      <button type="button" onClick={onOpenKnowledgeSettings} className="text-xs text-ink-500 hover:underline">
        Knowledge settings
      </button>
    </div>
  )
}

/**
 * The main product screen shown once GitHub is connected but no repository/
 * PR has been picked yet from the top-bar {@link RepoPrPicker} (ticket #128
 * follow-up) — the same canvas-shell chrome the Semantic Canvas itself uses
 * once a PR loads, so picking a PR feels like activating content already on
 * screen rather than navigating to a different app. Previously this state
 * was a separate "Select a repository" screen entirely.
 */
function EmptyCanvasShell({
  picker,
  onOpenGitHubAccess,
  onOpenKnowledgeSettings,
  onStartDiff,
}: {
  picker: React.ReactNode
  onOpenGitHubAccess: () => void
  onOpenKnowledgeSettings: () => void
  onStartDiff: () => void
}) {
  return (
    <div className="flex h-screen w-full flex-col overflow-hidden bg-canvas-paper text-canvas-ink">
      <AthenaTopBar
        picker={picker}
        right={
          <>
            <button type="button" onClick={onStartDiff} className="text-xs text-canvas-gold-deep hover:underline">
              Start a Diff
            </button>
            <button type="button" onClick={onOpenGitHubAccess} className="text-xs text-canvas-gold-deep hover:underline">
              GitHub Access
            </button>
            <button
              type="button"
              onClick={onOpenKnowledgeSettings}
              className="text-xs text-canvas-gold-deep hover:underline"
            >
              Knowledge
            </button>
          </>
        }
      />
      <div
        className="relative flex flex-1 items-center justify-center bg-[radial-gradient(var(--color-canvas-dot)_1.2px,transparent_1.2px)] bg-[length:26px_26px]"
      >
        <p className="text-sm font-medium tracking-wide text-canvas-ink-faint">
          Select a repository and Pull Request above to open the Semantic Canvas.
        </p>
      </div>
    </div>
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

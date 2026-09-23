import CoverageIndicator from './CoverageIndicator'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getChangeMap,
  getModules,
  getModuleSemanticProfile,
  getPullRequestSemanticProfile,
  getSemanticProfile,
  setReviewState,
  NoPullRequestSelectedError,
  NotConnectedError,
  type ChangeMapEntry,
  type ModuleNarrative,
  type ReviewState,
  type SemanticDimension,
  type SemanticDimensionEntry,
} from './api'
import ArchitectureLevel from './ArchitectureLevel'
import CapabilityLevel from './CapabilityLevel'
import FlowLevel from './FlowLevel'
import FrameworkLevel from './FrameworkLevel'
import { GUIDED_REVIEW_CHAPTERS } from './guidedReviewChapters'
import IntentLevel from './IntentLevel'
import PatternLevel from './PatternLevel'
import StructureLevel from './StructureLevel'
import {
  Card,
  DiffView,
  ErrorState,
  LEVEL_META,
  LoadingState,
  PrimaryButton,
  REVIEW_STATE_META,
  SecondaryButton,
  SectionLabel,
  StateDot,
} from './ui'

/**
 * The seven-level semantic spine (ticket #91 §2/§12), in Structure → Intent
 * order — the same order used to lay out the spine, walk the Change Story,
 * and default the initially-selected level. `label` is the UI-facing name;
 * `dimension` is the {@link SemanticDimension} it corresponds to on the
 * Change's Semantic Profile (ticket #94).
 */
const LEVELS: { label: string; dimension: SemanticDimension }[] = [
  { label: 'Structure', dimension: 'STRUCTURAL' },
  { label: 'Pattern', dimension: 'PATTERN' },
  { label: 'Framework', dimension: 'FRAMEWORK' },
  { label: 'Capability', dimension: 'RESPONSIBILITY' },
  { label: 'Flow', dimension: 'FEATURE' },
  { label: 'Architecture', dimension: 'ARCHITECTURE' },
  { label: 'Intent', dimension: 'INTENT' },
]

/**
 * What the Explorer is showing: the whole PR (its landing scope the instant
 * a PR is opened — ticket #91's approved mockup has no separate
 * category/change-list screen before the Explorer), one module narrowed
 * down from it, or one Change drilled into from either. All three render
 * through the same shell; only the data fetched (and, for 'change', the
 * Diff view toggle) differs.
 */
export type SemanticChangeExplorerScope =
  | { kind: 'pr' }
  | { kind: 'module'; moduleName: string }
  | { kind: 'change'; changeKey: string }

export default function SemanticChangeExplorerPage({
  scope,
  onScopeChange,
  onExitPr,
  onOpenDiffView,
  onOpenAiAnalysis,
  onOpenSummary,
  onNotConnected,
  onNoPullRequestSelected,
}: {
  scope: SemanticChangeExplorerScope
  /** Narrows to a module, or drills into one Change — replaces the current scope, doesn't stack. */
  onScopeChange: (scope: SemanticChangeExplorerScope) => void
  /** Leaves the PR entirely, back to repo/PR selection. */
  onExitPr: () => void
  /** Only ever called while `scope.kind === 'change'` — shows that Change's raw diff instead of the Explorer. */
  onOpenDiffView: (changeKey: string) => void
  onOpenAiAnalysis: () => void
  onOpenSummary: () => void
  /** Now that the Explorer is the landing view for a PR (ticket #122's follow-up), it
   * inherits the retired Change Map's own routing for a session that's gone stale. */
  onNotConnected: () => void
  onNoPullRequestSelected: () => void
}) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['semantic-profile', scope],
    queryFn: () => {
      switch (scope.kind) {
        case 'change':
          return getSemanticProfile(scope.changeKey)
        case 'module':
          return getModuleSemanticProfile(scope.moduleName)
        case 'pr':
          return getPullRequestSemanticProfile()
      }
    },
    retry: false,
  })
  // The Change Map's own data (ticket #122's follow-up: retiring the Change
  // Map as a destination doesn't retire what it showed — the PR title for
  // the topbar chip, and the flat per-Change list now living in the
  // evidence panel, both still come from here).
  const { data: changeMap } = useQuery({ queryKey: ['change-map'], queryFn: getChangeMap, retry: false })
  const { data: modules } = useQuery({ queryKey: ['modules'], queryFn: getModules, retry: false })
  const [currentDimension, setCurrentDimension] = useState<SemanticDimension>('STRUCTURAL')
  // Which entry of the current level is highlighted: a Structure chip the
  // reviewer clicked directly, or the Structure chip a Pattern's "supported
  // by" link sent them to (ticket #96); or a Capability card the reviewer
  // clicked, to show its evidence (ticket #97). Cleared on any other level
  // change — it's only meaningful while looking at Structure or Capability.
  const [selectedConceptName, setSelectedConceptName] = useState<string | undefined>(undefined)
  // The globally-selected concept driving cross-highlighting (ticket #99
  // §10): any classified concept, on any level, that the reviewer clicked
  // — a Change Story bracketed term, or (in a level's own selection
  // mechanism) whichever concept it reports as selected. Independent of
  // selectedConceptName/currentDimension: it survives a level change so
  // clicking a Change Story term keeps its cross-highlight active even
  // after switching levels.
  const [selectedGlobalEntry, setSelectedGlobalEntry] = useState<SemanticDimensionEntry | undefined>(undefined)
  // Guided Review (ticket #100 / #91 §14): undefined means the reviewer is
  // in the normal spine-driven Explorer; a chapter index means they're
  // walking GUIDED_REVIEW_CHAPTERS instead. Kept separate from
  // currentDimension so exiting can restore exactly the level the reviewer
  // was on before entering, without Guided Review's own navigation
  // disturbing it.
  const [guidedReviewChapterIndex, setGuidedReviewChapterIndex] = useState<number | undefined>(undefined)
  // The concept name the reviewer is currently hovering, if any (ticket
  // #101: "hovering a semantic element subtly pulses its supporting
  // evidence"). Purely transient UI feedback — unrelated to selection, so
  // it never affects selectedConceptName/selectedGlobalEntry and clears on
  // mouse-out rather than needing an explicit "unselect" action.
  const [hoveredConceptName, setHoveredConceptName] = useState<string | undefined>(undefined)

  if (isError) {
    if (error instanceof NotConnectedError) {
      onNotConnected()
      return null
    }
    if (error instanceof NoPullRequestSelectedError) {
      onNoPullRequestSelected()
      return null
    }
    return <ErrorState message="Could not load this Change's Semantic Profile." />
  }
  if (isLoading || !data) {
    return <LoadingState />
  }

  const dimensions = data.dimensions

  function selectLevel(dimension: SemanticDimension) {
    setCurrentDimension(dimension)
    setSelectedConceptName(undefined)
  }

  function selectSupportingStructuralChange(conceptName: string) {
    setCurrentDimension('STRUCTURAL')
    setSelectedConceptName(conceptName)
  }

  function selectGlobalConcept(entry: SemanticDimensionEntry) {
    setCurrentDimension(entry.dimension)
    setSelectedConceptName(undefined)
    setSelectedGlobalEntry(entry)
  }

  function entriesForDimension(dimension: SemanticDimension) {
    return dimensions.filter((entry) => entry.dimension === dimension)
  }

  function renderLevel(dimension: SemanticDimension) {
    const entries = entriesForDimension(dimension)
    const label = LEVELS.find((level) => level.dimension === dimension)!.label
    switch (dimension) {
      case 'STRUCTURAL':
        return (
          <StructureLevel entries={entries} selectedConceptName={selectedConceptName} onSelect={setSelectedConceptName} />
        )
      case 'PATTERN':
        return (
          <PatternLevel
            entries={entries}
            onSelectSupporting={selectSupportingStructuralChange}
            onSelectConcept={selectGlobalConcept}
            onHoverConcept={setHoveredConceptName}
          />
        )
      case 'FRAMEWORK':
        return <FrameworkLevel entries={entries} />
      case 'RESPONSIBILITY':
        return (
          <CapabilityLevel
            entries={entries}
            selectedConceptName={selectedConceptName}
            onSelect={(conceptName) => {
              setSelectedConceptName(conceptName)
              const entry = entries.find((e) => e.conceptName === conceptName)
              if (entry) {
                setSelectedGlobalEntry(entry)
              }
            }}
          />
        )
      case 'FEATURE':
        return <FlowLevel entries={entries} />
      case 'ARCHITECTURE':
        return <ArchitectureLevel entries={entries} onSelectConcept={selectGlobalConcept} />
      case 'INTENT':
        return <IntentLevel entries={entries} />
      default:
        return <CenterStage label={label} entry={entries[0]} />
    }
  }

  const topbar = (
    <ExplorerTopbar
      prTitle={changeMap?.prTitle}
      scope={scope}
      modules={modules}
      onScopeChange={onScopeChange}
      onExitPr={onExitPr}
      onOpenDiffView={scope.kind === 'change' ? () => onOpenDiffView(scope.changeKey) : undefined}
      onOpenAiAnalysis={onOpenAiAnalysis}
      onOpenSummary={onOpenSummary}
    />
  )

  if (guidedReviewChapterIndex !== undefined) {
    return (
      <div className="flex min-h-screen flex-col">
        {topbar}
        <GuidedReviewChapterView
          chapterIndex={guidedReviewChapterIndex}
          onBack={() => setGuidedReviewChapterIndex((index) => index! - 1)}
          onContinue={() => setGuidedReviewChapterIndex((index) => index! + 1)}
          onExit={() => setGuidedReviewChapterIndex(undefined)}
          renderLevel={renderLevel}
          allDimensions={dimensions}
        />
      </div>
    )
  }

  const entriesForCurrentDimension = entriesForDimension(currentDimension)
  const currentLabel = LEVELS.find((level) => level.dimension === currentDimension)!.label
  const highlightedEntries = entriesSharingEvidence(dimensions, selectedGlobalEntry)
  // The evidence panel shows the current level's own entries; while a
  // cross-highlight is active (ticket #99 §10), it shows every classified
  // entry across every dimension instead — connected ones highlighted,
  // everything else visually subdued rather than removed, per #91 §10's
  // closing rule ("unrelated content is visually subdued, not removed").
  const entriesForEvidencePanel = highlightedEntries.size > 0 ? dimensions : entriesForCurrentDimension
  const changesInScope = changeMap ? changesInCurrentScope(scope, changeMap.changes, modules) : []

  return (
    <div className="flex min-h-screen flex-col">
      {topbar}
      <CoverageIndicator />
      <div className="flex-1 px-6 py-10 sm:px-10 sm:py-14">
        <div className="animate-rise-in">
          <div className="mb-2 flex items-center justify-end">
            <SecondaryButton onClick={() => setGuidedReviewChapterIndex(0)}>Start guided review</SecondaryButton>
          </div>

          <ChangeStory dimensions={dimensions} onSelect={selectLevel} onSelectConcept={selectGlobalConcept} />

          <div className="grid gap-6 lg:grid-cols-[14rem_minmax(0,1fr)_24rem]">
            <Spine current={currentDimension} onSelect={selectLevel} />
            {/* Keyed on currentDimension so switching levels replays the rise-in
                animation (ticket #101: "moving between semantic levels
                transitions the same change into its new representation ...
                rather than reading as a new page load"), instead of a silent,
                instant swap — while keeping the spine/evidence panel around it
                stable, so the reviewer's place in the Explorer isn't disturbed. */}
            <div key={currentDimension} className="animate-rise-in">
              {renderLevel(currentDimension)}
            </div>
            <EvidencePanel
              label={currentLabel}
              entries={entriesForEvidencePanel}
              selectedConceptName={selectedConceptName}
              highlightedEntries={highlightedEntries}
              hoveredConceptName={hoveredConceptName}
              changesInScope={changesInScope}
              onSelectChange={(changeKey) => onScopeChange({ kind: 'change', changeKey })}
            />
          </div>
        </div>
      </div>
    </div>
  )
}

/**
 * The always-visible top bar (ticket #91 §12's `.topbar`, #122's follow-up):
 * wordmark, the current PR (with a module picker to narrow the same
 * Explorer down to one module, or back out to the whole PR — never a
 * separate screen), and the actions that used to live on the retired
 * Change Map (AI analysis, Review summary).
 */
function ExplorerTopbar({
  prTitle,
  scope,
  modules,
  onScopeChange,
  onExitPr,
  onOpenDiffView,
  onOpenAiAnalysis,
  onOpenSummary,
}: {
  prTitle: string | undefined
  scope: SemanticChangeExplorerScope
  modules: ModuleNarrative[] | undefined
  onScopeChange: (scope: SemanticChangeExplorerScope) => void
  onExitPr: () => void
  /** Present only while `scope.kind === 'change'` — the mockup's Diff view / Semantic
   * Explorer mode toggle only makes sense once a single Change is in focus. */
  onOpenDiffView: (() => void) | undefined
  onOpenAiAnalysis: () => void
  onOpenSummary: () => void
}) {
  const currentModuleName = scope.kind === 'module' ? scope.moduleName : undefined

  return (
    <header className="flex flex-wrap items-center justify-between gap-3 border-b border-ink-200 bg-paper-raised px-6 py-3.5 sm:px-10">
      <div className="flex min-w-0 items-center gap-3">
        <button
          type="button"
          onClick={onExitPr}
          className="font-display text-base font-medium tracking-tight text-ink-900 transition-colors hover:text-accent"
        >
          Athena
        </button>
        {prTitle && (
          <span className="inline-flex min-w-0 items-center gap-1.5 rounded-full border border-ink-200 bg-ink-50 py-1 pr-3 pl-1">
            <span className="truncate text-sm text-ink-700">{prTitle}</span>
          </span>
        )}
        {modules && modules.length > 1 && (
          <select
            aria-label="Scope to a module"
            value={currentModuleName ?? ''}
            onChange={(e) => {
              const moduleName = e.target.value
              onScopeChange(moduleName === '' ? { kind: 'pr' } : { kind: 'module', moduleName })
            }}
            className="rounded-lg border border-ink-200 bg-paper-raised px-2 py-1.5 text-sm text-ink-700 focus:border-accent focus:outline-none"
          >
            <option value="">Whole PR</option>
            {modules.map((module) => (
              <option key={module.moduleName} value={module.moduleName}>
                {module.moduleName}
              </option>
            ))}
          </select>
        )}
      </div>
      <div className="flex gap-2">
        {onOpenDiffView && <SecondaryButton onClick={onOpenDiffView}>Diff view</SecondaryButton>}
        <SecondaryButton onClick={onOpenAiAnalysis}>AI analysis</SecondaryButton>
        <PrimaryButton onClick={onOpenSummary}>Review summary</PrimaryButton>
      </div>
    </header>
  )
}

/**
 * The flat per-Change list (ticket #122's follow-up), scoped to whatever the
 * Explorer currently shows: every Change in the PR, one module's Changes, or
 * (trivially) the single Change already in focus. Replaces the retired
 * Change Map's own flat list — a detail reachable from the evidence panel
 * rather than a parallel destination.
 */
function changesInCurrentScope(
  scope: SemanticChangeExplorerScope,
  allChanges: ChangeMapEntry[],
  modules: ModuleNarrative[] | undefined,
): ChangeMapEntry[] {
  if (scope.kind === 'pr') {
    return allChanges
  }
  if (scope.kind === 'change') {
    return allChanges.filter((change) => change.changeKey === scope.changeKey)
  }
  const moduleChangeKeys = new Set(modules?.find((m) => m.moduleName === scope.moduleName)?.changeKeys ?? [])
  return allChanges.filter((change) => moduleChangeKeys.has(change.changeKey))
}

/**
 * The Guided Review chapter view (ticket #100 / #91 §14): walks
 * GUIDED_REVIEW_CHAPTERS in order, rendering each chapter's dimension(s)
 * side by side via the same per-level components the spine-driven Explorer
 * uses. The final "Inspect the evidence" chapter has no dimensions of its
 * own — it shows every classified entry's evidence instead, since it is
 * the change's raw evidence rather than another semantic level.
 */
function GuidedReviewChapterView({
  chapterIndex,
  onBack,
  onContinue,
  onExit,
  renderLevel,
  allDimensions,
}: {
  chapterIndex: number
  onBack: () => void
  onContinue: () => void
  onExit: () => void
  renderLevel: (dimension: SemanticDimension) => React.ReactNode
  allDimensions: SemanticDimensionEntry[]
}) {
  const chapter = GUIDED_REVIEW_CHAPTERS[chapterIndex]
  const isFirst = chapterIndex === 0
  const isLast = chapterIndex === GUIDED_REVIEW_CHAPTERS.length - 1

  return (
    <div className="px-6 py-10 sm:px-10 sm:py-14">
      <div className="animate-rise-in">
        <div className="mb-2 flex items-center justify-between">
          <SectionLabel>Chapter {chapterIndex + 1} of {GUIDED_REVIEW_CHAPTERS.length}</SectionLabel>
          <SecondaryButton onClick={onExit}>Exit guided review</SecondaryButton>
        </div>

        <h1 className="mb-6 text-2xl font-semibold tracking-tight text-ink-900">{chapter.title}</h1>

        <nav aria-label="Guided review chapters" className="mb-8">
          <ol className="flex flex-wrap items-center gap-2">
            {GUIDED_REVIEW_CHAPTERS.map((c, i) => {
              const primaryDimension = c.dimensions[0]
              const meta = primaryDimension ? LEVEL_META[primaryDimension] : undefined
              const isCurrent = i === chapterIndex
              return (
                <span key={c.title} className="flex items-center gap-2">
                  {i > 0 && <span className="h-px w-3 bg-ink-200" aria-hidden="true" />}
                  <li
                    aria-current={isCurrent ? 'true' : undefined}
                    className={
                      'rounded-full px-3 py-1 text-xs font-medium ' +
                      (isCurrent && meta
                        ? `${meta.soft} ${meta.text}`
                        : i < chapterIndex
                          ? 'bg-ink-200 text-ink-700'
                          : 'bg-ink-100 text-ink-500')
                    }
                  >
                    {c.title}
                  </li>
                </span>
              )
            })}
          </ol>
        </nav>

        {chapter.dimensions.length > 0 ? (
          <div className={'grid gap-6 ' + (chapter.dimensions.length > 1 ? 'lg:grid-cols-2' : 'lg:max-w-3xl')}>
            {chapter.dimensions.map((dimension) => (
              <div key={dimension}>{renderLevel(dimension)}</div>
            ))}
          </div>
        ) : (
          <EvidencePanel
            label="the Change"
            entries={allDimensions}
            selectedConceptName={undefined}
            highlightedEntries={new Set()}
          />
        )}

        <div className="mt-8 flex justify-between">
          {!isFirst ? <SecondaryButton onClick={onBack}>Back</SecondaryButton> : <span />}
          {!isLast && <PrimaryButton onClick={onContinue}>Continue</PrimaryButton>}
        </div>
      </div>
    </div>
  )
}

/**
 * Every classification (across all seven dimensions) that shares at least
 * one piece of evidence with {@code selected} — the real, derivable signal
 * cross-highlighting is built on (ticket #99 §10's "driven by the existing
 * evidence/matched-occurrence data ... not a new relationship model"):
 * every classifier passes a Change's own diff evidence verbatim, so two
 * classifications on different dimensions of the same Change genuinely
 * share evidence text when they're both derived from it. Always includes
 * {@code selected} itself. Empty (no highlighting active) when nothing is
 * selected.
 */
function entriesSharingEvidence(
  allEntries: SemanticDimensionEntry[],
  selected: SemanticDimensionEntry | undefined,
): Set<SemanticDimensionEntry> {
  if (!selected) {
    return new Set()
  }
  const selectedEvidence = new Set(selected.evidence)
  return new Set(
    allEntries.filter((entry) => entry === selected || entry.evidence.some((diff) => selectedEvidence.has(diff))),
  )
}

function Spine({
  current,
  onSelect,
}: {
  current: SemanticDimension
  onSelect: (dimension: SemanticDimension) => void
}) {
  return (
    <nav aria-label="Semantic levels">
      <SectionLabel>Semantic Spine</SectionLabel>
      <ul className="space-y-1">
        {LEVELS.map((level) => {
          const meta = LEVEL_META[level.dimension]
          const isCurrent = level.dimension === current
          return (
            <li key={level.dimension}>
              <button
                type="button"
                aria-current={isCurrent ? 'true' : undefined}
                onClick={() => onSelect(level.dimension)}
                className={
                  'flex w-full items-center gap-2.5 rounded-lg border px-3 py-2 text-left text-sm font-medium transition-colors ' +
                  (isCurrent
                    ? `border-transparent ${meta.soft} ${meta.text}`
                    : 'border-transparent text-ink-700 hover:bg-ink-100')
                }
              >
                <span aria-hidden="true" className={`h-2 w-2 shrink-0 rounded-full ${isCurrent ? 'bg-current' : meta.dot}`} />
                {level.label}
              </button>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

function CenterStage({ label, entry }: { label: string; entry: SemanticDimensionEntry | undefined }) {
  return (
    <section>
      <SectionLabel>{label}</SectionLabel>
      <Card className="p-5">
        {entry ? (
          <>
            <div className="mb-2 flex items-center gap-2">
              <h2 className="text-lg font-semibold text-ink-900">{entry.conceptName}</h2>
              <ConfidenceChip entry={entry} />
            </div>
            <p className="text-sm text-ink-700">{entry.conceptDescription}</p>
          </>
        ) : (
          <p className="text-sm text-ink-500">No {label} classification for this Change yet.</p>
        )}
      </Card>
    </section>
  )
}

function ConfidenceChip({ entry }: { entry: SemanticDimensionEntry }) {
  return (
    <span
      className={
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ' +
        (entry.inferred ? 'bg-amber-50 text-amber-800' : 'bg-emerald-50 text-emerald-800')
      }
    >
      {entry.inferred ? 'Inferred' : 'Observed'} · {entry.confidencePercent}%
    </span>
  )
}

function EvidencePanel({
  label,
  entries,
  selectedConceptName,
  highlightedEntries,
  hoveredConceptName,
  changesInScope = [],
  onSelectChange,
}: {
  label: string
  entries: SemanticDimensionEntry[]
  selectedConceptName: string | undefined
  highlightedEntries: Set<SemanticDimensionEntry>
  hoveredConceptName?: string
  /** Omitted in Guided Review's evidence chapter — that view is already a linear
   * walk, so a change-browser there would just duplicate the spine-driven Explorer. */
  changesInScope?: ChangeMapEntry[]
  onSelectChange?: (changeKey: string) => void
}) {
  const withEvidence = entries.filter((entry) => entry.evidence.length > 0)
  const crossHighlighting = highlightedEntries.size > 0
  return (
    <section className="space-y-6">
      <div>
        <SectionLabel>Evidence</SectionLabel>
        {withEvidence.length > 0 ? (
          <div className="space-y-3">
            {withEvidence.map((entry) => {
              const isSelected = entry.conceptName === selectedConceptName
              const isCrossHighlighted = highlightedEntries.has(entry)
              const isSubdued = crossHighlighting && !isCrossHighlighted
              const isHoverEmphasized = entry.conceptName === hoveredConceptName
              const meta = LEVEL_META[entry.dimension]
              return (
                <div
                  key={entry.dimension + ':' + entry.conceptName}
                  role="group"
                  aria-label={entry.conceptName}
                  aria-current={isSelected || isCrossHighlighted ? 'true' : undefined}
                  data-hover-emphasized={isHoverEmphasized ? 'true' : undefined}
                  className={
                    'space-y-3 rounded-xl transition-[opacity,box-shadow] duration-200 ' +
                    (isSelected || isCrossHighlighted ? 'ring-2 ring-accent ring-offset-2' : '') +
                    (isSubdued ? ' opacity-40' : '') +
                    (isHoverEmphasized ? ' ring-2 ring-accent/60' : '')
                  }
                >
                  <p className={`rounded-lg px-3 py-2 text-xs leading-relaxed text-ink-700 ${meta.soft}`}>
                    <span className={`font-semibold ${meta.text}`}>Why this counts as evidence:</span> the diff below is what{' '}
                    {entry.conceptName} was classified from.
                  </p>
                  {entry.evidence.map((diff, i) => (
                    <DiffView key={i} diff={diff} />
                  ))}
                </div>
              )
            })}
          </div>
        ) : (
          <p className="text-sm text-ink-500">No evidence available for the {label} level.</p>
        )}
      </div>
      {onSelectChange && <ChangesInScopeList changes={changesInScope} onSelectChange={onSelectChange} />}
    </section>
  )
}

/**
 * The flat per-Change list, folded into the evidence panel rather than kept
 * as the retired Change Map's own full-page destination (ticket #122's
 * follow-up) — a detail of what the Explorer is currently scoped to, not a
 * parallel place to browse Changes from. Collapsed by default: on a 360-Change
 * PR this list is exactly the wall of undifferentiated rows the Explorer
 * exists to replace as the *primary* view, so it stays out of the way until
 * a reviewer explicitly wants to browse it directly.
 */
function ChangesInScopeList({
  changes,
  onSelectChange,
}: {
  changes: ChangeMapEntry[]
  onSelectChange: (changeKey: string) => void
}) {
  const [expanded, setExpanded] = useState(false)
  if (changes.length === 0) {
    return null
  }
  return (
    <div>
      <button
        type="button"
        onClick={() => setExpanded((e) => !e)}
        className="text-xs font-medium text-ink-500 hover:text-accent"
      >
        {expanded ? 'Hide' : 'Show'} all {changes.length} Change{changes.length === 1 ? '' : 's'} in scope
      </button>
      {expanded && (
        <ul className="mt-2 space-y-1 border-t border-ink-100 pt-2">
          {changes.map((change) => (
            <li key={change.changeKey} className="flex items-center gap-2">
              <ReviewStateControl changeKey={change.changeKey} state={change.reviewState} />
              <button
                type="button"
                onClick={() => onSelectChange(change.changeKey)}
                className="min-w-0 flex-1 truncate text-left text-xs text-ink-600 hover:text-accent hover:underline"
              >
                {change.description}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** One Change's review state (ticket #122's follow-up to the retired Change Map's own
 * per-row state pills), cycling Unseen -> Understanding -> Reviewed -> Concern -> Skipped
 * on click — the same states/order the Change Map used, just relocated here. */
function ReviewStateControl({ changeKey, state }: { changeKey: string; state: ReviewState }) {
  const queryClient = useQueryClient()
  const STATE_CYCLE: ReviewState[] = ['UNSEEN', 'UNDERSTANDING', 'REVIEWED', 'CONCERN', 'SKIPPED']
  const mutation = useMutation({
    mutationFn: (next: ReviewState) => setReviewState(changeKey, next),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['change-map'] }),
  })
  const meta = REVIEW_STATE_META[state]
  return (
    <button
      type="button"
      title={meta.label}
      onClick={() => {
        const nextIndex = (STATE_CYCLE.indexOf(state) + 1) % STATE_CYCLE.length
        mutation.mutate(STATE_CYCLE[nextIndex])
      }}
      className="shrink-0 rounded-full p-0.5 hover:bg-ink-100"
    >
      <StateDot state={state} />
    </button>
  )
}

/**
 * The interactive sentence bridging the diff to its meaning (ticket #91
 * §11). This ticket's scope is the click-to-select mechanic and reflecting
 * the Change's real classifications — not the fully-templated narrative
 * prose from #91's own example (which needs data this Semantic Profile
 * doesn't carry, like the enclosing type name), so classified concepts are
 * joined plainly, in spine order. Clicking a bracketed term also drives
 * cross-highlighting (ticket #99 §10): {@code onSelectConcept} carries the
 * actual entry, not just its dimension, so the Explorer can highlight every
 * other classification sharing its evidence.
 */
function ChangeStory({
  dimensions,
  onSelect,
  onSelectConcept,
}: {
  dimensions: SemanticDimensionEntry[]
  onSelect: (dimension: SemanticDimension) => void
  onSelectConcept: (entry: SemanticDimensionEntry) => void
}) {
  const byDimension = new Map(dimensions.map((entry) => [entry.dimension, entry]))
  // Structure is the raw evidence layer ("the diff is the evidence"), not part of the
  // narrated story — #91 §11's own example sentence starts at Pattern, never Structure.
  const ordered = LEVELS.filter((level) => level.dimension !== 'STRUCTURAL')
    .map((level) => byDimension.get(level.dimension))
    .filter((entry): entry is SemanticDimensionEntry => entry !== undefined)

  if (ordered.length === 0) {
    return null
  }

  return (
    <div className="mb-8">
      <p className="mb-2 text-xs font-semibold tracking-wide text-ink-500 uppercase">The change, in one sentence</p>
      <p className="font-display text-2xl leading-relaxed font-medium text-ink-900">
        {ordered.map((entry, i) => {
          const meta = LEVEL_META[entry.dimension]
          return (
            <span key={entry.dimension}>
              {i > 0 && <span className="text-ink-300"> → </span>}
              <button
                type="button"
                onClick={() => {
                  onSelect(entry.dimension)
                  onSelectConcept(entry)
                }}
                className={`rounded px-0.5 italic underline decoration-current/40 decoration-2 underline-offset-4 transition-colors ${meta.text} ${meta.hoverSoft}`}
              >
                {entry.conceptName}
              </button>
            </span>
          )
        })}
      </p>
    </div>
  )
}

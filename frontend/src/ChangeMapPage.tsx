import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  connectAi,
  getAiStatus,
  getChangeMap,
  getModuleNarrative,
  getModules,
  setReviewState,
  NoPullRequestSelectedError,
  NotConnectedError,
  type ChangeCategory,
  type ChangeMapEntry,
  type ClassGroup,
  type ModuleNarrative,
  type ReviewState,
  type TransformationKind,
} from './api'
import {
  CATEGORY_META,
  CategoryBadge,
  Card,
  CountChip,
  ErrorState,
  LoadingState,
  PageShell,
  PrimaryButton,
  REVIEW_STATE_META,
  SecondaryButton,
  StateDot,
} from './ui'

const ANTHROPIC_API_KEYS_URL = 'https://console.anthropic.com/settings/keys'

// Behavioral changes are what a reviewer's attention should go to first — the
// interface leads with Intent/Impact (visual-design-philosophy.md), so this
// category ordering, not alphabetical or API order, drives the summary and
// the section order in the grouped Change list below.
const CATEGORY_ORDER: ChangeCategory[] = ['BEHAVIORAL', 'STRUCTURAL', 'MECHANICAL', 'UNKNOWN']

// The specific kind of transformation reads far better than the coarse category alone — several
// distinct kinds (rename, move, add, remove, signature change, extract) all fall under
// "Structural," so a row showing only that tells the reviewer almost nothing about what to expect.
const KIND_LABELS: Record<TransformationKind, string> = {
  RENAME_SYMBOL: 'Rename',
  MOVE_SYMBOL: 'Move',
  ADD_SYMBOL: 'Add',
  REMOVE_SYMBOL: 'Remove',
  CHANGE_METHOD_SIGNATURE: 'Signature change',
  EXTRACT_METHOD: 'Extract method',
  MECHANICAL_REPLACEMENT: 'Mechanical rename',
  FORMATTING_ONLY: 'Formatting',
}

export default function ChangeMapPage({
  onNotConnected,
  onNoPullRequestSelected,
  onSelectChange,
  onOpenPreSubmissionSummary,
  onOpenAiAnalysis,
}: {
  onNotConnected: () => void
  onNoPullRequestSelected: () => void
  onSelectChange: (changeKey: string) => void
  onOpenPreSubmissionSummary: () => void
  onOpenAiAnalysis: () => void
}) {
  const queryClient = useQueryClient()
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['change-map'],
    queryFn: getChangeMap,
    retry: false,
  })
  const { data: aiStatus } = useQuery({
    queryKey: ['ai-status'],
    queryFn: getAiStatus,
  })
  const [showingConnectClaude, setShowingConnectClaude] = useState(false)

  const reviewStateMutation = useMutation({
    mutationFn: ({ changeKey, state }: { changeKey: string; state: ReviewState }) => setReviewState(changeKey, state),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['change-map'] }),
  })

  if (isError) {
    if (error instanceof NotConnectedError) {
      onNotConnected()
      return null
    }
    if (error instanceof NoPullRequestSelectedError) {
      onNoPullRequestSelected()
      return null
    }
    return <ErrorState message="Could not load the Change Map." />
  }

  if (isLoading || !data) {
    return <LoadingState />
  }

  return (
    <PageShell wide>
      <div className="mb-10 flex flex-col items-start justify-between gap-6 sm:flex-row sm:items-end">
        <PrUnderstandingSummary prTitle={data.prTitle} categoryCounts={data.categoryCounts} />
        <div className="flex flex-col items-end gap-2">
          <div className="flex gap-2">
            {aiStatus?.configured === false ? (
              <SecondaryButton
                onClick={() => {
                  if (!showingConnectClaude) {
                    window.open(ANTHROPIC_API_KEYS_URL, '_blank', 'noreferrer')
                  }
                  setShowingConnectClaude((shown) => !shown)
                }}
              >
                Connect Claude
              </SecondaryButton>
            ) : (
              <SecondaryButton onClick={onOpenAiAnalysis}>AI analysis</SecondaryButton>
            )}
            <PrimaryButton onClick={onOpenPreSubmissionSummary}>Review summary</PrimaryButton>
          </div>
          {showingConnectClaude && (
            <ConnectClaudePanel
              onConnected={() => {
                setShowingConnectClaude(false)
                queryClient.invalidateQueries({ queryKey: ['ai-status'] })
              }}
            />
          )}
        </div>
      </div>
      <ModuleNarrativesSection
        changes={data.changes}
        aiConfigured={aiStatus?.configured === true}
        onSelectChange={onSelectChange}
      />
      <ChangeMapGroups
        changes={data.changes}
        classGroups={data.classGroups ?? []}
        onSelectChange={onSelectChange}
        onSetReviewState={(changeKey, state) => reviewStateMutation.mutate({ changeKey, state })}
      />
    </PageShell>
  )
}

function ConnectClaudePanel({ onConnected }: { onConnected: () => void }) {
  const [apiKey, setApiKey] = useState('')
  const mutation = useMutation({
    mutationFn: connectAi,
    onSuccess: onConnected,
  })

  return (
    <Card className="w-72 p-3">
      <p className="mb-2 text-sm text-ink-700">
        Create a key on the{' '}
        <a
          href={ANTHROPIC_API_KEYS_URL}
          target="_blank"
          rel="noreferrer"
          className="text-accent underline underline-offset-2 hover:text-ink-900"
        >
          Anthropic Console
        </a>
        , then paste it here. Athena remembers it — this is only needed once.
      </p>
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          mutation.mutate(apiKey)
        }}
      >
        <input
          type="password"
          autoComplete="off"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
          placeholder="sk-ant-..."
          className="min-w-0 flex-1 rounded-lg border border-ink-200 px-2 py-1 text-sm focus:border-accent focus:outline-none"
        />
        <PrimaryButton
          type="submit"
          disabled={mutation.isPending || apiKey.trim().length === 0}
          className="shrink-0 px-3 py-1"
        >
          {mutation.isPending ? 'Saving…' : 'Save'}
        </PrimaryButton>
      </form>
      {mutation.isError && (
        <p className="mt-2 text-sm text-red-600">{(mutation.error as Error).message}</p>
      )}
    </Card>
  )
}

function ModuleNarrativesSection({
  changes,
  aiConfigured,
  onSelectChange,
}: {
  changes: ChangeMapEntry[]
  aiConfigured: boolean
  onSelectChange: (changeKey: string) => void
}) {
  const { data: modules } = useQuery({
    queryKey: ['modules'],
    queryFn: getModules,
    retry: false,
  })

  // A single module (or none) has nothing to disambiguate — "why does this
  // module exist" only earns its place once a PR actually spans more than one.
  if (!modules || modules.length < 2) {
    return null
  }

  const descriptionByKey = new Map(changes.map((c) => [c.changeKey, c.description]))

  return (
    <section className="mb-10">
      <p className="mb-4 text-xs font-semibold tracking-wide text-ink-500 uppercase">What changed and why</p>
      <div className="space-y-3">
        {modules.map((module) => (
          <ModuleNarrativeCard
            key={module.moduleName}
            module={module}
            descriptionByKey={descriptionByKey}
            aiConfigured={aiConfigured}
            onSelectChange={onSelectChange}
          />
        ))}
      </div>
    </section>
  )
}

function ModuleNarrativeCard({
  module,
  descriptionByKey,
  aiConfigured,
  onSelectChange,
}: {
  module: ModuleNarrative
  descriptionByKey: Map<string, string>
  aiConfigured: boolean
  onSelectChange: (changeKey: string) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const narrativeQuery = useQuery({
    queryKey: ['module-narrative', module.moduleName],
    queryFn: () => getModuleNarrative(module.moduleName),
    enabled: false,
    retry: false,
  })

  const narrative = narrativeQuery.data?.narrative ?? null

  return (
    <Card className="p-4">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="font-mono text-sm font-semibold text-ink-900">{module.moduleName}</span>
            <CountChip className="bg-ink-100 text-ink-700">
              {module.changeKeys.length} change{module.changeKeys.length === 1 ? '' : 's'}
            </CountChip>
          </div>
          {narrative ? (
            <p className="mt-1.5 text-sm text-ink-700">{narrative}</p>
          ) : narrativeQuery.isFetching ? (
            <p className="mt-1.5 text-sm text-ink-400">Thinking…</p>
          ) : narrativeQuery.isError ? (
            <p className="mt-1.5 text-sm text-red-600">Could not generate a narrative for this module.</p>
          ) : (
            <p className="mt-1.5 text-sm text-ink-400">No narrative generated yet.</p>
          )}
        </div>
        {!narrative && aiConfigured && (
          <SecondaryButton
            onClick={() => narrativeQuery.refetch()}
            disabled={narrativeQuery.isFetching}
            className="shrink-0"
          >
            {narrativeQuery.isFetching ? 'Explaining…' : 'Explain'}
          </SecondaryButton>
        )}
      </div>
      <button
        type="button"
        className="mt-2 text-xs font-medium text-ink-500 hover:text-accent"
        onClick={() => setExpanded((e) => !e)}
      >
        {expanded ? 'Hide' : 'Show'} the {module.changeKeys.length} Change{module.changeKeys.length === 1 ? '' : 's'} in this module
      </button>
      {expanded && (
        <ul className="mt-2 space-y-1 border-t border-ink-100 pt-2">
          {module.changeKeys.map((changeKey) => (
            <li key={changeKey}>
              <button
                type="button"
                className="text-xs text-ink-600 underline-offset-2 hover:text-accent hover:underline"
                onClick={() => onSelectChange(changeKey)}
              >
                {descriptionByKey.get(changeKey) ?? changeKey}
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function PrUnderstandingSummary({
  prTitle,
  categoryCounts,
}: {
  prTitle: string
  categoryCounts: Record<ChangeCategory, number>
}) {
  const total = CATEGORY_ORDER.reduce((sum, c) => sum + categoryCounts[c], 0)

  return (
    <section>
      <p className="mb-1.5 text-xs font-semibold tracking-widest text-ink-500 uppercase">Intent</p>
      <h1 className="mb-6 text-3xl font-semibold tracking-tight text-ink-900">{prTitle}</h1>
      {total > 0 && (
        <div className="mb-5 flex h-2 w-full max-w-md overflow-hidden rounded-full bg-ink-100">
          {CATEGORY_ORDER.filter((c) => categoryCounts[c] > 0).map((category) => (
            <span
              key={category}
              className={`h-full bg-gradient-to-r ${CATEGORY_META[category].gradient}`}
              style={{ width: `${(categoryCounts[category] / total) * 100}%` }}
            />
          ))}
        </div>
      )}
      <dl className="flex flex-wrap gap-x-7 gap-y-4">
        {CATEGORY_ORDER.map((category) => {
          const meta = CATEGORY_META[category]
          return (
            <div key={category} className="flex items-center gap-3">
              <CategoryBadge category={category} size="md" />
              <div>
                <dt className="text-xs font-medium text-ink-500">{meta.label}</dt>
                <dd className={`text-2xl leading-tight font-bold tabular-nums ${meta.text}`}>
                  {categoryCounts[category]}
                </dd>
              </div>
            </div>
          )
        })}
      </dl>
    </section>
  )
}

function ChangeMapGroups({
  changes,
  classGroups,
  onSelectChange,
  onSetReviewState,
}: {
  changes: ChangeMapEntry[]
  classGroups: ClassGroup[]
  onSelectChange: (changeKey: string) => void
  onSetReviewState: (changeKey: string, state: ReviewState) => void
}) {
  if (changes.length === 0) {
    return (
      <Card className="px-4 py-8 text-center">
        <p className="text-sm text-ink-500">No Changes detected.</p>
      </Card>
    )
  }

  // Every entry belongs to exactly one class group (the backend partitions the
  // full entry list), so within a category we can walk classGroups in order
  // and just filter each group's own entries down to that category.
  const groups = CATEGORY_ORDER.map((category) => ({
    category,
    changes: changes.filter((change) => change.category === category),
  })).filter((group) => group.changes.length > 0)

  return (
    <div>
      <p className="mb-4 text-xs font-semibold tracking-wide text-ink-500 uppercase">Semantic Changes</p>
      <div className="space-y-8">
        {groups.map((group, i) => (
          <CategoryGroup
            key={group.category}
            category={group.category}
            changes={group.changes}
            classGroups={classGroups}
            style={{ animationDelay: `${i * 60}ms` }}
            onSelectChange={onSelectChange}
            onSetReviewState={onSetReviewState}
          />
        ))}
      </div>
    </div>
  )
}

function CategoryGroup({
  category,
  changes,
  classGroups,
  style,
  onSelectChange,
  onSetReviewState,
}: {
  category: ChangeCategory
  changes: ChangeMapEntry[]
  classGroups: ClassGroup[]
  style?: React.CSSProperties
  onSelectChange: (changeKey: string) => void
  onSetReviewState: (changeKey: string, state: ReviewState) => void
}) {
  const meta = CATEGORY_META[category]
  const totalOccurrences = changes.reduce((sum, c) => sum + Math.max(c.occurrenceCount, 1), 0)

  // Walk this category's own changes in order, consulting classGroups only to
  // decide which consecutive-by-class runs collapse into one row. An entry
  // absent from every class group (e.g. classGroups wasn't supplied at all)
  // still renders on its own, so grouping is a display refinement, never a
  // precondition for a Change actually showing up.
  const enclosingTypeByKey = new Map<string, string>()
  for (const group of classGroups) {
    if (group.entries.length <= 1) continue
    for (const entry of group.entries) {
      enclosingTypeByKey.set(entry.changeKey, group.enclosingType)
    }
  }
  const groupsInCategory: { enclosingType: string; entries: ChangeMapEntry[] }[] = []
  for (const change of changes) {
    const enclosingType = enclosingTypeByKey.get(change.changeKey)
    const last = groupsInCategory[groupsInCategory.length - 1]
    if (enclosingType && last?.enclosingType === enclosingType) {
      last.entries.push(change)
    } else {
      groupsInCategory.push({ enclosingType: enclosingType ?? '', entries: [change] })
    }
  }

  return (
    <section className="animate-rise-in" style={style}>
      <Card className="overflow-hidden">
        <div className={`flex items-center gap-3 border-b border-ink-200 px-5 py-4 ${meta.soft}`}>
          <CategoryBadge category={category} size="md" />
          <div className="flex flex-1 items-baseline gap-2.5">
            <h3 className={`text-base font-semibold ${meta.text}`}>{meta.label}</h3>
            <CountChip className="bg-white/70 text-ink-700 shadow-sm">
              {changes.length} change{changes.length === 1 ? '' : 's'}
            </CountChip>
            {totalOccurrences > changes.length && (
              <span className="text-xs font-medium text-ink-500">{totalOccurrences} occurrences</span>
            )}
          </div>
        </div>
        <ul className="divide-y divide-ink-200">
          {groupsInCategory.map((group) =>
            group.entries.length > 1 && group.enclosingType ? (
              <CollapsibleClassGroup
                key={group.enclosingType}
                enclosingType={group.enclosingType}
                entries={group.entries}
                onSelectChange={onSelectChange}
                onSetReviewState={onSetReviewState}
              />
            ) : (
              group.entries.map((change) => (
                <ChangeRow
                  key={change.id}
                  change={change}
                  onSelectChange={onSelectChange}
                  onSetReviewState={onSetReviewState}
                />
              ))
            ),
          )}
        </ul>
      </Card>
    </section>
  )
}

function CollapsibleClassGroup({
  enclosingType,
  entries,
  onSelectChange,
  onSetReviewState,
}: {
  enclosingType: string
  entries: ChangeMapEntry[]
  onSelectChange: (changeKey: string) => void
  onSetReviewState: (changeKey: string, state: ReviewState) => void
}) {
  const [expanded, setExpanded] = useState(false)

  return (
    <li>
      <button
        type="button"
        onClick={() => setExpanded((e) => !e)}
        aria-expanded={expanded}
        className="flex w-full items-center gap-3 px-5 py-3 text-left transition-colors hover:bg-ink-50"
      >
        <span
          className={`text-ink-400 transition-transform ${expanded ? 'rotate-90' : ''}`}
          aria-hidden="true"
        >
          ▸
        </span>
        <span className="font-mono text-sm font-medium text-ink-900">{enclosingType}</span>
        <CountChip className="bg-ink-100 text-ink-700">
          {entries.length} changes
        </CountChip>
      </button>
      {expanded && (
        <ul className="divide-y divide-ink-100 border-t border-ink-100 bg-ink-50/50 pl-4">
          {entries.map((change) => (
            <ChangeRow
              key={change.id}
              change={change}
              onSelectChange={onSelectChange}
              onSetReviewState={onSetReviewState}
            />
          ))}
        </ul>
      )}
    </li>
  )
}

function ChangeRow({
  change,
  onSelectChange,
  onSetReviewState,
}: {
  change: ChangeMapEntry
  onSelectChange: (changeKey: string) => void
  onSetReviewState: (changeKey: string, state: ReviewState) => void
}) {
  return (
    <li className="group relative flex items-center gap-4 px-5 py-4 transition-colors hover:bg-ink-50">
      <StateDot state={change.reviewState} />
      <button className="min-w-0 flex-1 text-left" onClick={() => onSelectChange(change.changeKey)}>
        <p className="truncate text-sm font-medium text-ink-900 transition-colors group-hover:text-accent">
          {change.description}
        </p>
        <p className="mt-0.5 text-xs text-ink-500">
          <span>{KIND_LABELS[change.kind]}</span>
          {change.occurrenceCount > 1 && (
            <>
              <span className="mx-1.5 text-ink-300">·</span>
              <span className="font-medium text-ink-700">×{change.occurrenceCount} occurrences</span>
            </>
          )}
          {change.exceptionCount > 0 && (
            <>
              <span className="mx-1.5 text-ink-300">·</span>
              <span className="font-medium text-amber-700">
                {change.exceptionCount} exception{change.exceptionCount === 1 ? '' : 's'}
              </span>
            </>
          )}
          <span className="sr-only">
            {' '}
            · {CATEGORY_META[change.category].label}
          </span>
        </p>
      </button>
      <ReviewStateSelect
        changeId={change.id}
        description={change.description}
        value={change.reviewState}
        onChange={(state) => onSetReviewState(change.changeKey, state)}
      />
      <span className="text-ink-300 opacity-0 transition-opacity group-hover:opacity-100" aria-hidden="true">
        →
      </span>
    </li>
  )
}

function ReviewStateSelect({
  changeId,
  description,
  value,
  onChange,
}: {
  changeId: number
  description: string
  value: ReviewState
  onChange: (state: ReviewState) => void
}) {
  const meta = REVIEW_STATE_META[value]
  return (
    <div className="relative shrink-0">
      <label className="sr-only" htmlFor={`review-state-${changeId}`}>
        Review state for {description}
      </label>
      <select
        id={`review-state-${changeId}`}
        className={`cursor-pointer appearance-none rounded-full py-1 pr-7 pl-3 text-xs font-medium ring-1 ring-inset focus:outline-none ${meta.bg} ${meta.text} ${meta.ring}`}
        value={value}
        onClick={(e) => e.stopPropagation()}
        onChange={(e) => onChange(e.target.value as ReviewState)}
      >
        {(Object.keys(REVIEW_STATE_META) as ReviewState[]).map((state) => (
          <option key={state} value={state}>
            {REVIEW_STATE_META[state].label}
          </option>
        ))}
      </select>
    </div>
  )
}

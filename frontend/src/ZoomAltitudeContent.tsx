import { useState } from 'react'
import type { SemanticDimension, SemanticDimensionEntry, SemanticProfile } from './api'
import type { AltitudeStop } from './ZoomAltitudeRail'
import { entriesByStop } from './ZoomAltitudeRail'
import { fileItemId } from './canvasItemId'

/**
 * Content rendered at one zoom-altitude stop, inside a dived-into territory
 * (ticket #130). Reuses the same {@link SemanticProfile} data the retired
 * Explorer's per-level components consumed — the difference here is
 * spatial node placement instead of a card list, plus new content this
 * ticket introduces (Architecture layer boxes, test-suite aggregation,
 * config-file icons) that didn't exist in the old Explorer. Node styling
 * matches the approved prototype's card system (ticket #128).
 *
 * REST-endpoint chips and client-adapter markers are NOT implemented here:
 * that needs new classifier-level detection (parsing e.g. @RestController/
 * @GetMapping annotations and cross-module adapter calls) that doesn't
 * exist anywhere in the codebase yet — out of scope for this ticket's pass,
 * flagged back to the ticket rather than faked.
 */

const TEST_FILE_PATTERN = /(Test|Tests|IT)\.(java|ts|tsx|js|jsx)$|\.test\.(ts|tsx|js|jsx)$/
const CONFIG_FILE_PATTERN = /(^|\/)(pom\.xml|package\.json|application[\w.-]*\.ya?ml|application[\w.-]*\.properties|tsconfig[\w.-]*\.json|vite\.config\.\w+|build\.gradle\w*)$/

function isTestFile(path: string): boolean {
  return TEST_FILE_PATTERN.test(path)
}

function isConfigFile(path: string): boolean {
  return CONFIG_FILE_PATTERN.test(path)
}

// Staggered node-appear (ticket #133): each node in a newly-revealed altitude
// stop animates in .animate-canvas-node-appear with an increasing delay, so
// the stop reads as nodes converging in sequence rather than popping in at
// once. Its own `@media (prefers-reduced-motion: reduce)` rule collapses
// this to no animation — no separate handling needed here.
const STAGGER_STEP_MS = 40

function staggerStyle(index: number): React.CSSProperties {
  return { animationDelay: `${index * STAGGER_STEP_MS}ms` }
}

/** What was clicked, enough for the detail drawer (ticket #131) to render the right content
 * without needing to re-derive it from just a bare node name. */
export type NodeSelection =
  | { kind: 'concept'; entry: SemanticDimensionEntry }
  | { kind: 'file'; fileName: string; owningEntry: SemanticDimensionEntry }

// A short label plus a one-line "what this means here" section title, per
// dimension (the approved prototype's DIMENSION_SECTION_TITLE) — shown as
// the active dimension tab's section heading so a reviewer landing on a
// crowded territory reads "this group is the business responsibility"
// instead of an unlabeled wall of tiles.
const DIMENSION_META: Record<SemanticDimension, { tabLabel: string; sectionTitle: string }> = {
  RESPONSIBILITY: { tabLabel: 'Capability', sectionTitle: 'Capability — the business responsibility this adds or changes' },
  FEATURE: { tabLabel: 'Flow', sectionTitle: 'Flow — where this sits in the application' },
  ARCHITECTURE: { tabLabel: 'Architecture', sectionTitle: 'Architecture — how it is shaped inside' },
  PATTERN: { tabLabel: 'Pattern', sectionTitle: 'Pattern — recognizable technique' },
  FRAMEWORK: { tabLabel: 'Framework', sectionTitle: 'Framework — the mechanism used' },
  STRUCTURAL: { tabLabel: 'Structure', sectionTitle: 'Structure — the atomic edits' },
  INTENT: { tabLabel: 'Intent', sectionTitle: 'Intent — why' },
}

/**
 * Groups a stop's entries by their real {@link SemanticDimension} and, when
 * a stop spans more than one dimension (Capability+Flow = RESPONSIBILITY +
 * FEATURE; Pattern+Framework = PATTERN + FRAMEWORK), shows one dimension at
 * a time behind tabs instead of flattening every entry into one undivided
 * grid — the prototype's `dimension-tabs` (ticket #128). A stop with only
 * one dimension present renders it directly with no tab chrome, so the
 * common single-dimension case stays exactly as immediate as before.
 */
function groupByDimension(entries: SemanticDimensionEntry[]): { dimension: SemanticDimension; entries: SemanticDimensionEntry[] }[] {
  const order: SemanticDimension[] = ['RESPONSIBILITY', 'FEATURE', 'ARCHITECTURE', 'PATTERN', 'FRAMEWORK', 'STRUCTURAL', 'INTENT']
  const groups: { dimension: SemanticDimension; entries: SemanticDimensionEntry[] }[] = []
  for (const dimension of order) {
    const forDimension = entries.filter((e) => e.dimension === dimension)
    if (forDimension.length > 0) {
      groups.push({ dimension, entries: forDimension })
    }
  }
  return groups
}

export default function ZoomAltitudeContent({
  stop,
  profile,
  showLayerBadges,
  onSelectNode,
  moduleName,
  commentCounts,
  showCommentedOnly,
  onOpenComments,
}: {
  stop: AltitudeStop
  profile: SemanticProfile
  showLayerBadges: boolean
  onSelectNode: (selection: NodeSelection) => void
  /** The owning territory's module name (ticket #134) — needed to build a file node's
   * comment-target id, which is scoped by territory since a file name alone isn't
   * globally unique. */
  moduleName: string
  /** Comment count per canvas item id, for file-node pin badges (ticket #134). */
  commentCounts: Record<string, number>
  showCommentedOnly: boolean
  onOpenComments: (itemId: string, itemLabel: string) => void
}) {
  const entries = entriesByStop(profile).get(stop) ?? []
  const groups = groupByDimension(entries)
  const [activeDimension, setActiveDimension] = useState<SemanticDimension | undefined>(undefined)
  const selected = groups.find((g) => g.dimension === activeDimension) ?? groups[0]

  if (groups.length === 0) {
    return null
  }

  return (
    <div role="region" aria-label={`${stop} altitude content`}>
      {groups.length > 1 && (
        <div role="tablist" aria-label="Semantic dimension" className="flex flex-wrap gap-1 border-b border-canvas-line px-6 pt-4">
          {groups.map((group) => (
            <button
              key={group.dimension}
              type="button"
              role="tab"
              aria-selected={group.dimension === selected.dimension}
              onClick={() => setActiveDimension(group.dimension)}
              className={`-mb-px border-b-2 px-3.5 py-2 text-[13px] font-semibold ${
                group.dimension === selected.dimension
                  ? 'border-canvas-gold text-canvas-gold-deep'
                  : 'border-transparent text-canvas-ink-faint hover:text-canvas-ink'
              }`}
            >
              {DIMENSION_META[group.dimension].tabLabel}
            </button>
          ))}
        </div>
      )}
      <div className="px-6 pt-5">
        <h2 className="mb-3 text-[11px] font-bold uppercase tracking-wide text-canvas-ink-faint">
          {DIMENSION_META[selected.dimension].sectionTitle}
        </h2>
      </div>
      <DimensionGroupContent
        stop={stop}
        entries={selected.entries}
        showLayerBadges={showLayerBadges}
        onSelectNode={onSelectNode}
        moduleName={moduleName}
        commentCounts={commentCounts}
        showCommentedOnly={showCommentedOnly}
        onOpenComments={onOpenComments}
      />
    </div>
  )
}

function DimensionGroupContent({
  stop,
  entries,
  showLayerBadges,
  onSelectNode,
  moduleName,
  commentCounts,
  showCommentedOnly,
  onOpenComments,
}: {
  stop: AltitudeStop
  entries: SemanticDimensionEntry[]
  showLayerBadges: boolean
  onSelectNode: (selection: NodeSelection) => void
  moduleName: string
  commentCounts: Record<string, number>
  showCommentedOnly: boolean
  onOpenComments: (itemId: string, itemLabel: string) => void
}) {
  if (stop === 'STRUCTURE') {
    return (
      <StructureContent
        entries={entries}
        showLayerBadges={showLayerBadges}
        onSelectNode={onSelectNode}
        moduleName={moduleName}
        commentCounts={commentCounts}
        showCommentedOnly={showCommentedOnly}
        onOpenComments={onOpenComments}
      />
    )
  }
  if (stop === 'ARCHITECTURE') {
    return <ArchitectureContent entries={entries} showLayerBadges={showLayerBadges} onSelectNode={onSelectNode} />
  }
  return (
    <div className="flex flex-wrap gap-3 px-6 pb-6">
      {entries.map((entry, index) => (
        <ConceptNode
          key={entry.conceptName}
          entry={entry}
          index={index}
          showLayerBadges={showLayerBadges}
          onSelectNode={onSelectNode}
        />
      ))}
    </div>
  )
}

function DimensionBadge({ dimension }: { dimension: string }) {
  return (
    <span
      data-testid="dimension-badge"
      className="absolute right-1.5 top-1.5 z-[2] rounded bg-canvas-gold-soft px-1.5 py-0.5 text-[8.5px] font-bold uppercase tracking-wide text-canvas-gold-deep"
    >
      {dimension}
    </span>
  )
}

function ConceptNode({
  entry,
  index,
  showLayerBadges,
  onSelectNode,
}: {
  entry: SemanticDimensionEntry
  index: number
  showLayerBadges: boolean
  onSelectNode: (selection: NodeSelection) => void
}) {
  return (
    <button
      type="button"
      data-testid="concept-node"
      data-dimension={entry.dimension}
      className="animate-canvas-node-appear relative min-w-[180px] max-w-[220px] overflow-hidden rounded-xl border-[1.5px] border-canvas-line-strong bg-canvas-paper-raised text-left shadow-[var(--shadow-canvas)] transition-[box-shadow,transform] hover:-translate-y-px hover:shadow-[var(--shadow-canvas-lift)] motion-reduce:animate-none"
      style={staggerStyle(index)}
      onClick={() => onSelectNode({ kind: 'concept', entry })}
    >
      {showLayerBadges && <DimensionBadge dimension={entry.dimension} />}
      <div className="border-b border-canvas-line bg-canvas-gold-soft px-2 py-1.5 font-display text-[12.5px] font-semibold uppercase tracking-wide text-canvas-gold-deep">
        {entry.dimension}
      </div>
      <div className="px-3 py-2.5">
        <h4 className="mb-1 font-display text-[14.5px] font-medium leading-tight text-canvas-ink">{entry.conceptName}</h4>
        <p className="text-[11.5px] leading-snug text-canvas-ink-soft">{entry.conceptDescription}</p>
      </div>
    </button>
  )
}

function ArchitectureContent({
  entries,
  showLayerBadges,
  onSelectNode,
}: {
  entries: SemanticDimensionEntry[]
  showLayerBadges: boolean
  onSelectNode: (selection: NodeSelection) => void
}) {
  return (
    <div data-testid="architecture-layer-shape" className="flex flex-wrap gap-3 px-6 pb-6">
      {entries.map((entry, index) => (
        <button
          key={entry.conceptName}
          type="button"
          data-testid="concept-node"
          data-dimension={entry.dimension}
          className="animate-canvas-node-appear relative min-w-[150px] rounded-xl border-2 border-dashed border-canvas-line-strong bg-transparent px-3 py-2.5 text-left transition-colors hover:border-canvas-gold motion-reduce:animate-none"
          style={staggerStyle(index)}
          onClick={() => onSelectNode({ kind: 'concept', entry })}
        >
          {showLayerBadges && <DimensionBadge dimension={entry.dimension} />}
          <div className="mb-1 text-[10.5px] font-semibold uppercase tracking-wide text-canvas-ink-faint">
            {entry.conceptName}
          </div>
          <p className="text-[11px] leading-snug text-canvas-ink-faint">{entry.conceptDescription}</p>
        </button>
      ))}
    </div>
  )
}

function StructureContent({
  entries,
  showLayerBadges,
  onSelectNode,
  moduleName,
  commentCounts,
  showCommentedOnly,
  onOpenComments,
}: {
  entries: SemanticDimensionEntry[]
  showLayerBadges: boolean
  onSelectNode: (selection: NodeSelection) => void
  moduleName: string
  commentCounts: Record<string, number>
  showCommentedOnly: boolean
  onOpenComments: (itemId: string, itemLabel: string) => void
}) {
  const fileOwner = new Map<string, SemanticDimensionEntry>()
  for (const entry of entries) {
    for (const file of entry.filesTouched ?? []) {
      if (!fileOwner.has(file)) {
        fileOwner.set(file, entry)
      }
    }
  }
  const allFiles = Array.from(fileOwner.keys())
  const productionFiles = allFiles.filter((f) => !isTestFile(f))
  const testFiles = allFiles.filter(isTestFile)
  const [testSuiteExpanded, setTestSuiteExpanded] = useState(false)

  return (
    <div className="flex flex-wrap gap-2.5 px-6 pb-6">
      {productionFiles.map((file, index) => {
        const itemId = fileItemId(moduleName, file)
        const commentCount = commentCounts[itemId] ?? 0
        const dimmed = showCommentedOnly && commentCount === 0
        return (
          <div key={file} className={`relative transition-opacity ${dimmed ? 'opacity-35' : ''}`}>
            <button
              type="button"
              data-testid="file-node"
              data-file-kind={isConfigFile(file) ? 'config' : 'production'}
              className="animate-canvas-node-appear relative min-w-[160px] max-w-[220px] rounded-xl border-[1.5px] border-canvas-line-strong bg-canvas-paper-raised px-2.5 py-2 text-left shadow-[var(--shadow-canvas)] transition-[box-shadow,transform] hover:-translate-y-px hover:shadow-[var(--shadow-canvas-lift)] motion-reduce:animate-none"
              style={staggerStyle(index)}
              onClick={() => onSelectNode({ kind: 'file', fileName: file, owningEntry: fileOwner.get(file)! })}
            >
              {showLayerBadges && <DimensionBadge dimension="STRUCTURAL" />}
              <span className="mb-1 flex items-center gap-1.5" aria-hidden="true" data-testid={isConfigFile(file) ? 'config-icon' : 'file-icon'}>
                {isConfigFile(file) ? <GearIcon /> : <FileIcon />}
              </span>
              <span className="break-words font-mono text-[11.5px] font-medium text-canvas-ink">{file}</span>
            </button>
            {commentCount > 0 && (
              <button
                type="button"
                aria-label={`${commentCount} comment${commentCount === 1 ? '' : 's'} on ${file}`}
                data-testid="comment-pin"
                onClick={(e) => {
                  e.stopPropagation()
                  onOpenComments(itemId, file)
                }}
                className="absolute -right-1.5 -top-1.5 z-[3] flex items-center gap-0.5 rounded-full border border-canvas-gold bg-canvas-gold-soft px-1.5 py-0.5 text-[10px] font-bold text-canvas-gold-deep hover:shadow-[var(--shadow-canvas)]"
              >
                💬 {commentCount}
              </button>
            )}
          </div>
        )
      })}
      {testFiles.length > 0 && (
        <div
          role="group"
          aria-label="Test suite"
          data-testid="test-suite-node"
          className="min-w-[160px] max-w-[220px] rounded-xl border-2 border-dashed border-canvas-territory-idle bg-canvas-paper px-2.5 py-2"
        >
          <button
            type="button"
            className="flex items-center gap-1.5 text-left text-[12px] font-medium text-canvas-ink"
            onClick={() => setTestSuiteExpanded((current) => !current)}
          >
            <FlaskIcon />
            Test suite ({testFiles.length} files)
          </button>
          {testSuiteExpanded && (
            <ul className="mt-2 space-y-1 font-mono text-[10.5px] text-canvas-ink-faint">
              {testFiles.map((file) => (
                <li key={file}>{file}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

function FileIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="flex-shrink-0 text-canvas-ink-faint">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
    </svg>
  )
}

function GearIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="flex-shrink-0 text-canvas-ink-faint">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  )
}

function FlaskIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="flex-shrink-0 text-canvas-territory-idle">
      <path d="M9 3h6" />
      <path d="M10 3v6l-5.5 9.5A1 1 0 0 0 5.36 20h13.28a1 1 0 0 0 .86-1.5L14 9V3" />
      <path d="M7.5 15h9" />
    </svg>
  )
}

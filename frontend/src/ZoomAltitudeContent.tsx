import { useState } from 'react'
import type { SemanticDimensionEntry, SemanticProfile } from './api'
import type { AltitudeStop } from './ZoomAltitudeRail'
import { entriesByStop } from './ZoomAltitudeRail'

/**
 * Content rendered at one zoom-altitude stop, inside a dived-into territory
 * (ticket #130). Reuses the same {@link SemanticProfile} data the retired
 * Explorer's per-level components consumed — the difference here is
 * spatial node placement instead of a card list, plus new content this
 * ticket introduces (Architecture layer boxes, test-suite aggregation,
 * config-file icons) that didn't exist in the old Explorer.
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

/** What was clicked, enough for the detail drawer (ticket #131) to render the right content
 * without needing to re-derive it from just a bare node name. */
export type NodeSelection =
  | { kind: 'concept'; entry: SemanticDimensionEntry }
  | { kind: 'file'; fileName: string; owningEntry: SemanticDimensionEntry }

export default function ZoomAltitudeContent({
  stop,
  profile,
  showLayerBadges,
  onSelectNode,
}: {
  stop: AltitudeStop
  profile: SemanticProfile
  showLayerBadges: boolean
  onSelectNode: (selection: NodeSelection) => void
}) {
  const entries = entriesByStop(profile).get(stop) ?? []

  if (stop === 'STRUCTURE') {
    return <StructureContent entries={entries} showLayerBadges={showLayerBadges} onSelectNode={onSelectNode} />
  }
  if (stop === 'ARCHITECTURE') {
    return <ArchitectureContent entries={entries} showLayerBadges={showLayerBadges} onSelectNode={onSelectNode} />
  }
  return (
    <div role="region" aria-label={`${stop} altitude content`} className="flex flex-wrap gap-4 p-6">
      {entries.map((entry) => (
        <ConceptNode key={entry.conceptName} entry={entry} showLayerBadges={showLayerBadges} onSelectNode={onSelectNode} />
      ))}
    </div>
  )
}

function ConceptNode({
  entry,
  showLayerBadges,
  onSelectNode,
}: {
  entry: SemanticDimensionEntry
  showLayerBadges: boolean
  onSelectNode: (selection: NodeSelection) => void
}) {
  return (
    <button
      type="button"
      data-testid="concept-node"
      data-dimension={entry.dimension}
      className="min-w-[180px] rounded-xl border border-ink-200 bg-paper-raised p-4 text-left shadow-sm hover:bg-ink-100"
      onClick={() => onSelectNode({ kind: 'concept', entry })}
    >
      {showLayerBadges && (
        <span data-testid="dimension-badge" className="mb-1 block text-[10px] uppercase tracking-wide text-ink-500">
          {entry.dimension}
        </span>
      )}
      <h4 className="font-medium text-ink-900">{entry.conceptName}</h4>
      <p className="text-xs text-ink-500">{entry.conceptDescription}</p>
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
    <div role="region" aria-label="Architecture altitude content" className="p-6">
      <div data-testid="architecture-layer-shape" className="flex flex-col gap-3">
        {entries.map((entry) => (
          <ConceptNode key={entry.conceptName} entry={entry} showLayerBadges={showLayerBadges} onSelectNode={onSelectNode} />
        ))}
      </div>
    </div>
  )
}

function StructureContent({
  entries,
  showLayerBadges,
  onSelectNode,
}: {
  entries: SemanticDimensionEntry[]
  showLayerBadges: boolean
  onSelectNode: (selection: NodeSelection) => void
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
    <div role="region" aria-label="Structure altitude content" className="flex flex-wrap gap-4 p-6">
      {productionFiles.map((file) => (
        <button
          key={file}
          type="button"
          data-testid="file-node"
          data-file-kind={isConfigFile(file) ? 'config' : 'production'}
          className="min-w-[160px] rounded-xl border border-ink-200 bg-paper-raised p-4 text-left shadow-sm hover:bg-ink-100"
          onClick={() => onSelectNode({ kind: 'file', fileName: file, owningEntry: fileOwner.get(file)! })}
        >
          {showLayerBadges && (
            <span data-testid="dimension-badge" className="mb-1 block text-[10px] uppercase tracking-wide text-ink-500">
              STRUCTURAL
            </span>
          )}
          <span aria-hidden="true" data-testid={isConfigFile(file) ? 'config-icon' : 'file-icon'}>
            {isConfigFile(file) ? '⚙' : '📄'}
          </span>
          <span className="ml-1 text-sm text-ink-900">{file}</span>
        </button>
      ))}
      {testFiles.length > 0 && (
        <div
          role="group"
          aria-label="Test suite"
          data-testid="test-suite-node"
          className="min-w-[160px] rounded-xl border border-dashed border-ink-300 bg-paper p-4"
        >
          <button
            type="button"
            className="text-left text-sm font-medium text-ink-900"
            onClick={() => setTestSuiteExpanded((current) => !current)}
          >
            🧪 Test suite ({testFiles.length} files)
          </button>
          {testSuiteExpanded && (
            <ul className="mt-2 space-y-1 text-xs text-ink-500">
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

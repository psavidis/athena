import { useMemo, useState } from 'react'
import { useQueries } from '@tanstack/react-query'
import { getChangeDetail, type ChangeCategory, type ModuleTopology, type TransformationKind } from './api'
import { LoadingState } from './ui'

/**
 * File-First review mode (ticket #132): a flat, searchable file list
 * grouped by module, for an experienced reviewer who wants to reach
 * Files → Diff in one click rather than being routed through Athena's
 * semantic explanation first. Built from the same territory data the
 * canvas shell already fetches, resolved down to individual files via
 * each territory's changeKeys.
 *
 * Per-file aggregate +/- line counts are NOT shown: no diff data in this
 * codebase is attributed at file granularity (a Change's diff can span
 * multiple filesTouched with no per-file split) — showing a fabricated
 * number would be worse than omitting it. A row shows its Change's real
 * category/kind instead. Flagged on the ticket rather than faked.
 */

const CONFIG_FILE_PATTERN = /(^|\/)(pom\.xml|package\.json|application[\w.-]*\.ya?ml|application[\w.-]*\.properties|tsconfig[\w.-]*\.json|vite\.config\.\w+|build\.gradle\w*)$/
const NEW_CODE_KINDS: TransformationKind[] = ['ADD_CLASS', 'ADD_SYMBOL', 'ADD_FIELD']

function isConfigFile(path: string): boolean {
  return CONFIG_FILE_PATTERN.test(path)
}

export interface FileRow {
  fileName: string
  moduleName: string
  category: ChangeCategory
  kind: TransformationKind
  changeKey: string
  isNew: boolean
}

type QuickFilter = 'ALL' | 'NEW_CODE' | 'HAS_CONTEXT'

export default function FileFirstMode({
  topology,
  contextFiles,
  onOpenFile,
  onExplainFile,
}: {
  topology: ModuleTopology
  /** File names that have a matching node somewhere on the semantic canvas — used for "Has context". */
  contextFiles: Set<string>
  onOpenFile: (row: FileRow) => void
  onExplainFile: (row: FileRow) => void
}) {
  const changeKeys = useMemo(
    () => Array.from(new Set(topology.territories.flatMap((t) => t.changeKeys))),
    [topology],
  )
  const changeQueries = useQueries({
    queries: changeKeys.map((changeKey) => ({
      queryKey: ['change-detail', changeKey],
      queryFn: () => getChangeDetail(changeKey),
      retry: false,
    })),
  })
  const isLoading = changeQueries.some((q) => q.isLoading)

  const rows = useMemo(() => {
    const result: FileRow[] = []
    const moduleByChangeKey = new Map<string, string>()
    for (const territory of topology.territories) {
      for (const changeKey of territory.changeKeys) {
        moduleByChangeKey.set(changeKey, territory.moduleName)
      }
    }
    for (const query of changeQueries) {
      const detail = query.data
      if (!detail) {
        continue
      }
      const moduleName = moduleByChangeKey.get(detail.changeKey) ?? '(unknown)'
      for (const file of detail.files) {
        result.push({
          fileName: file,
          moduleName,
          category: detail.category,
          kind: detail.kind,
          changeKey: detail.changeKey,
          isNew: NEW_CODE_KINDS.includes(detail.kind),
        })
      }
    }
    return result
  }, [topology, changeQueries])

  const [searchTerm, setSearchTerm] = useState('')
  const [quickFilter, setQuickFilter] = useState<QuickFilter>('ALL')
  const [collapsedModules, setCollapsedModules] = useState<Set<string>>(new Set())

  const filteredRows = rows.filter((row) => {
    const term = searchTerm.trim().toLowerCase()
    if (term && !row.fileName.toLowerCase().includes(term) && !row.moduleName.toLowerCase().includes(term)) {
      return false
    }
    if (quickFilter === 'NEW_CODE' && !row.isNew) {
      return false
    }
    if (quickFilter === 'HAS_CONTEXT' && !contextFiles.has(row.fileName)) {
      return false
    }
    return true
  })

  const rowsByModule = new Map<string, FileRow[]>()
  for (const row of filteredRows) {
    const list = rowsByModule.get(row.moduleName) ?? []
    list.push(row)
    rowsByModule.set(row.moduleName, list)
  }

  function toggleModule(moduleName: string) {
    setCollapsedModules((current) => {
      const next = new Set(current)
      if (next.has(moduleName)) {
        next.delete(moduleName)
      } else {
        next.add(moduleName)
      }
      return next
    })
  }

  return (
    <div role="region" aria-label="File-First file list" className="flex h-full flex-1 flex-col overflow-hidden bg-canvas-paper">
      <div className="flex flex-wrap items-center gap-3.5 border-b border-canvas-line bg-canvas-paper-raised px-5 py-3">
        <div className="flex min-w-[220px] flex-1 items-center gap-2 rounded-lg border border-canvas-line-strong bg-canvas-paper px-3 py-1.5 text-canvas-ink-faint">
          <SearchIcon />
          <input
            type="text"
            role="searchbox"
            aria-label="Search files"
            placeholder="Filter by path or module…"
            className="flex-1 bg-transparent text-[13px] text-canvas-ink outline-none"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <div className="flex gap-1.5">
          <button
            type="button"
            aria-pressed={quickFilter === 'NEW_CODE'}
            className={`rounded-full border px-2.5 py-1 text-[11.5px] font-semibold ${
              quickFilter === 'NEW_CODE'
                ? 'border-canvas-gold bg-canvas-gold-soft text-canvas-gold-deep'
                : 'border-canvas-line-strong bg-canvas-paper text-canvas-ink-soft'
            }`}
            onClick={() => setQuickFilter((current) => (current === 'NEW_CODE' ? 'ALL' : 'NEW_CODE'))}
          >
            New code
          </button>
          <button
            type="button"
            aria-pressed={quickFilter === 'HAS_CONTEXT'}
            className={`rounded-full border px-2.5 py-1 text-[11.5px] font-semibold ${
              quickFilter === 'HAS_CONTEXT'
                ? 'border-canvas-gold bg-canvas-gold-soft text-canvas-gold-deep'
                : 'border-canvas-line-strong bg-canvas-paper text-canvas-ink-soft'
            }`}
            onClick={() => setQuickFilter((current) => (current === 'HAS_CONTEXT' ? 'ALL' : 'HAS_CONTEXT'))}
          >
            Has context
          </button>
        </div>
      </div>
      <div className="flex-1 overflow-auto py-1">
        {isLoading && <LoadingState />}
        {!isLoading &&
          Array.from(rowsByModule.entries()).map(([moduleName, moduleRows]) => {
            const collapsed = collapsedModules.has(moduleName)
            return (
              <div key={moduleName} className="mb-0.5">
                <button
                  type="button"
                  className="flex w-full items-center gap-2.5 border-b border-canvas-line bg-canvas-paper px-5 py-2.5 text-left hover:bg-canvas-gold-soft"
                  onClick={() => toggleModule(moduleName)}
                >
                  <span className={`inline-block transition-transform ${collapsed ? '-rotate-90' : ''}`}>
                    <ChevronIcon />
                  </span>
                  <span className="font-mono text-[13px] font-semibold text-canvas-ink">{moduleName}</span>
                  <span className="text-[11.5px] text-canvas-ink-faint">{moduleRows.length} files</span>
                </button>
                {!collapsed && (
                  <ul>
                    {moduleRows.map((row) => (
                      <li
                        key={row.fileName}
                        className="flex items-center gap-3 border-b border-canvas-line px-5 py-2 hover:bg-canvas-gold-soft"
                      >
                        <button
                          type="button"
                          data-testid="file-first-row"
                          className="flex flex-1 items-center gap-2 text-left"
                          onClick={() => onOpenFile(row)}
                        >
                          <span
                            aria-hidden="true"
                            className="flex-shrink-0 text-canvas-ink-faint"
                            data-testid={isConfigFile(row.fileName) ? 'config-icon' : 'file-icon'}
                          >
                            {isConfigFile(row.fileName) ? <GearIcon /> : <FileIcon />}
                          </span>
                          <span className="font-mono text-[12.5px] text-canvas-ink">{row.fileName}</span>
                        </button>
                        {contextFiles.has(row.fileName) && (
                          <button
                            type="button"
                            data-testid="explain-this"
                            className="flex-shrink-0 whitespace-nowrap rounded-md border border-canvas-line-strong px-2 py-1 text-[11px] font-semibold text-canvas-gold-deep hover:border-canvas-gold hover:bg-canvas-gold-soft"
                            onClick={() => onExplainFile(row)}
                          >
                            Explain this
                          </button>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )
          })}
      </div>
    </div>
  )
}

function SearchIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="11" cy="11" r="7" />
      <line x1="21" y1="21" x2="16.6" y2="16.6" />
    </svg>
  )
}

function ChevronIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" className="text-canvas-ink-faint">
      <polyline points="6 9 12 15 18 9" />
    </svg>
  )
}

function FileIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
    </svg>
  )
}

function GearIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  )
}

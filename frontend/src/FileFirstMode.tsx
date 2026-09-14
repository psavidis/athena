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
    <div role="region" aria-label="File-First file list" className="h-screen overflow-auto bg-paper p-6">
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <input
          type="text"
          role="searchbox"
          aria-label="Search files"
          placeholder="Search files or modules…"
          className="w-64 rounded-lg border border-ink-200 bg-paper-raised px-3 py-2 text-sm"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
        <button
          type="button"
          aria-pressed={quickFilter === 'NEW_CODE'}
          className={`rounded-full border px-3 py-1 text-xs ${
            quickFilter === 'NEW_CODE' ? 'border-accent bg-accent-soft text-accent' : 'border-ink-200 bg-paper-raised'
          }`}
          onClick={() => setQuickFilter((current) => (current === 'NEW_CODE' ? 'ALL' : 'NEW_CODE'))}
        >
          New code
        </button>
        <button
          type="button"
          aria-pressed={quickFilter === 'HAS_CONTEXT'}
          className={`rounded-full border px-3 py-1 text-xs ${
            quickFilter === 'HAS_CONTEXT' ? 'border-accent bg-accent-soft text-accent' : 'border-ink-200 bg-paper-raised'
          }`}
          onClick={() => setQuickFilter((current) => (current === 'HAS_CONTEXT' ? 'ALL' : 'HAS_CONTEXT'))}
        >
          Has context
        </button>
      </div>
      {isLoading && <LoadingState />}
      {!isLoading &&
        Array.from(rowsByModule.entries()).map(([moduleName, moduleRows]) => {
          const collapsed = collapsedModules.has(moduleName)
          return (
            <div key={moduleName} className="mb-3 rounded-xl border border-ink-200 bg-paper-raised">
              <button
                type="button"
                className="flex w-full items-center justify-between px-4 py-2 text-left text-sm font-medium text-ink-900"
                onClick={() => toggleModule(moduleName)}
              >
                <span>{moduleName}</span>
                <span className="text-xs text-ink-500">{moduleRows.length} files</span>
              </button>
              {!collapsed && (
                <ul>
                  {moduleRows.map((row) => (
                    <li key={row.fileName} className="flex items-center justify-between border-t border-ink-100 px-4 py-2">
                      <button
                        type="button"
                        data-testid="file-first-row"
                        className="flex flex-1 items-center gap-2 text-left text-sm text-ink-900 hover:underline"
                        onClick={() => onOpenFile(row)}
                      >
                        <span aria-hidden="true" data-testid={isConfigFile(row.fileName) ? 'config-icon' : 'file-icon'}>
                          {isConfigFile(row.fileName) ? '⚙' : '📄'}
                        </span>
                        {row.fileName}
                      </button>
                      {contextFiles.has(row.fileName) && (
                        <button
                          type="button"
                          data-testid="explain-this"
                          className="rounded-full border border-ink-200 px-2 py-1 text-xs text-ink-700 hover:bg-ink-100"
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
  )
}

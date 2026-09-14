import { useQuery } from '@tanstack/react-query'
import { getChangeDetail, type ModuleTopology, type SemanticDimensionEntry } from './api'
import { DiffView, LoadingState } from './ui'

/**
 * What's currently selected on the canvas, driving the detail drawer's
 * content (ticket #131). A concept node shows its description plus linked
 * chips; a Structure-level file node shows its diff plus a one-line
 * evidence statement tying it back to the concept the reviewer arrived
 * from; the PR-overview node shows a footprint summary instead of a diff.
 */
export type DrawerSelection =
  | { kind: 'concept'; entry: SemanticDimensionEntry }
  | { kind: 'file'; fileName: string; fromConceptName: string; changeKeys: string[] }
  | { kind: 'overview' }

export default function DetailDrawer({
  selection,
  topology,
  onClose,
  onJumpToFile,
}: {
  selection: DrawerSelection | undefined
  topology: ModuleTopology
  onClose: () => void
  onJumpToFile: (fileName: string) => void
}) {
  if (!selection) {
    return null
  }
  return (
    <div
      role="dialog"
      aria-label="Detail drawer"
      className="absolute inset-x-0 bottom-0 z-10 max-h-[45vh] overflow-auto rounded-t-2xl border-t border-ink-200 bg-paper-raised p-6 shadow-lg"
    >
      <button
        type="button"
        aria-label="Close detail drawer"
        className="absolute right-4 top-4 rounded-full border border-ink-200 px-2 py-1 text-xs text-ink-500 hover:bg-ink-100"
        onClick={onClose}
      >
        Close
      </button>
      {selection.kind === 'concept' && <ConceptDrawerContent entry={selection.entry} onJumpToFile={onJumpToFile} />}
      {selection.kind === 'file' && <FileDrawerContent selection={selection} />}
      {selection.kind === 'overview' && <OverviewDrawerContent topology={topology} />}
    </div>
  )
}

function ConceptDrawerContent({
  entry,
  onJumpToFile,
}: {
  entry: SemanticDimensionEntry
  onJumpToFile: (fileName: string) => void
}) {
  const files = entry.filesTouched ?? []
  return (
    <div>
      <h3 className="font-display text-xl text-ink-900">{entry.conceptName}</h3>
      <p className="mt-1 text-sm text-ink-700">{entry.conceptDescription}</p>
      {files.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2">
          {files.map((file) => (
            <button
              key={file}
              type="button"
              data-testid="drawer-file-chip"
              className="rounded-full border border-ink-200 bg-paper px-3 py-1 text-xs text-ink-700 hover:bg-ink-100"
              onClick={() => onJumpToFile(file)}
            >
              {file}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function FileDrawerContent({
  selection,
}: {
  selection: { fileName: string; fromConceptName: string; changeKeys: string[] }
}) {
  const { data, isLoading } = useQuery({
    queryKey: ['drawer-file-diff', selection.fileName, selection.changeKeys],
    queryFn: async () => {
      for (const changeKey of selection.changeKeys) {
        const detail = await getChangeDetail(changeKey)
        if (detail.files.includes(selection.fileName)) {
          return detail
        }
      }
      return null
    },
    enabled: selection.changeKeys.length > 0,
    retry: false,
  })

  return (
    <div>
      <h3 className="font-display text-xl text-ink-900">{selection.fileName}</h3>
      <p className="mt-1 text-sm text-ink-500" data-testid="evidence-statement">
        This file is evidence for &ldquo;{selection.fromConceptName}&rdquo;.
      </p>
      <div className="mt-4">
        {isLoading && <LoadingState />}
        {data && <DiffView diff={data.diff} />}
      </div>
    </div>
  )
}

function OverviewDrawerContent({ topology }: { topology: ModuleTopology }) {
  const maxFiles = Math.max(1, ...topology.territories.map((t) => t.fileCount))
  return (
    <div data-testid="footprint-summary">
      <h3 className="font-display text-xl text-ink-900">Footprint</h3>
      <div className="mt-4 space-y-2">
        {topology.territories.map((territory) => (
          <div key={territory.moduleName}>
            <div className="flex justify-between text-xs text-ink-500">
              <span>{territory.moduleName}</span>
              <span>{territory.fileCount} files</span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-ink-100">
              <div
                className="h-full rounded-full bg-accent"
                style={{ width: `${(territory.fileCount / maxFiles) * 100}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

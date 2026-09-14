import { useQuery } from '@tanstack/react-query'
import { getChangeDetail, type ModuleTopology, type SemanticDimensionEntry } from './api'
import { DiffView, LoadingState } from './ui'

/**
 * What's currently selected on the canvas, driving the detail drawer's
 * content (ticket #131). A concept node shows its description plus linked
 * chips; a Structure-level file node shows its diff plus a one-line
 * evidence statement tying it back to the concept the reviewer arrived
 * from; the PR-overview node shows a footprint summary instead of a diff.
 * Shell styling matches the approved prototype (ticket #128); DiffView
 * itself stays the app's shared ink/paper diff renderer, reused unmodified
 * rather than forked into a second gold-themed diff surface.
 */
export type DrawerSelection =
  | { kind: 'concept'; entry: SemanticDimensionEntry }
  | { kind: 'file'; fileName: string; fromConceptName: string; changeKeys: string[] }
  | { kind: 'overview' }

const KIND_LABEL: Record<DrawerSelection['kind'], string> = {
  concept: 'Concept',
  file: 'File',
  overview: 'PR Overview',
}

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
  const title =
    selection.kind === 'concept' ? selection.entry.conceptName : selection.kind === 'file' ? selection.fileName : 'Footprint'
  return (
    <div
      role="dialog"
      aria-label="Detail drawer"
      className="absolute inset-x-0 bottom-0 z-30 flex max-h-[46%] flex-col rounded-t-2xl border-t border-canvas-line bg-canvas-paper-raised shadow-[0_-8px_24px_rgba(42,38,32,0.1)]"
    >
      <div className="flex items-center justify-between gap-3 px-5 pb-2.5 pt-3.5">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="flex-shrink-0 whitespace-nowrap rounded-full bg-canvas-gold-soft px-2.5 py-0.5 text-[10.5px] font-semibold uppercase tracking-wide text-canvas-gold-deep">
            {KIND_LABEL[selection.kind]}
          </span>
          <span
            className={
              selection.kind === 'concept'
                ? 'min-w-0 overflow-hidden text-ellipsis whitespace-nowrap font-display text-[17px] font-medium text-canvas-ink'
                : 'min-w-0 overflow-hidden text-ellipsis whitespace-nowrap font-mono text-[15px] font-medium text-canvas-ink'
            }
          >
            {title}
          </span>
        </div>
        <button
          type="button"
          aria-label="Close detail drawer"
          className="flex-shrink-0 rounded-md p-1 text-canvas-ink-faint hover:bg-canvas-line hover:text-canvas-ink"
          onClick={onClose}
        >
          ✕
        </button>
      </div>
      <div className="flex-1 overflow-auto px-5 pb-4.5">
        {selection.kind === 'concept' && <ConceptDrawerContent entry={selection.entry} onJumpToFile={onJumpToFile} />}
        {selection.kind === 'file' && <FileDrawerContent selection={selection} />}
        {selection.kind === 'overview' && <OverviewDrawerContent topology={topology} />}
      </div>
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
      <p className="max-w-[70ch] text-[13.5px] leading-relaxed text-canvas-ink-soft">{entry.conceptDescription}</p>
      {files.length > 0 && (
        <div className="mt-3.5">
          <h4 className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-canvas-ink-faint">Files</h4>
          <div className="flex flex-wrap gap-1.5">
            {files.map((file) => (
              <button
                key={file}
                type="button"
                data-testid="drawer-file-chip"
                className="rounded-lg border border-canvas-line-strong bg-canvas-paper px-2.5 py-1.5 font-mono text-xs text-canvas-ink hover:border-canvas-gold hover:bg-canvas-gold-soft"
                onClick={() => onJumpToFile(file)}
              >
                {file}
              </button>
            ))}
          </div>
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
      <p className="mb-3.5 text-[12.5px] leading-relaxed text-canvas-ink-soft" data-testid="evidence-statement">
        This file is evidence for &ldquo;{selection.fromConceptName}&rdquo;.
      </p>
      {isLoading && <LoadingState />}
      {!isLoading && <DiffView diff={data?.diff ?? ''} />}
    </div>
  )
}

function OverviewDrawerContent({ topology }: { topology: ModuleTopology }) {
  const maxFiles = Math.max(1, ...topology.territories.map((t) => t.fileCount))
  return (
    <div data-testid="footprint-summary" className="flex flex-col gap-2.5">
      {topology.territories.map((territory) => (
        <div key={territory.moduleName}>
          <div className="mb-1 flex justify-between font-mono text-[11.5px] text-canvas-ink-faint">
            <span>{territory.moduleName}</span>
            <span>{territory.fileCount} files</span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-canvas-line">
            <div
              className="h-full rounded-full bg-canvas-gold"
              style={{ width: `${(territory.fileCount / maxFiles) * 100}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  )
}

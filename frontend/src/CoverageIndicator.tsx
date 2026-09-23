import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { getRawDiff, getUnrepresentedFiles } from './api'
import type { UnrepresentedFile } from './api'
import { DiffView, SecondaryButton } from './ui'

/**
 * Tells the reviewer when Athena's analysis leaves changed files unrepresented, and lets them
 * open the raw diff of each such file (ticket #261) — so an incomplete review never looks
 * complete. Renders nothing while loading, on error, or when every changed file is
 * represented. A review with no Changes at all gets an explicit empty state with the file list
 * already open, instead of an empty canvas.
 */
export default function CoverageIndicator() {
  const { data } = useQuery({ queryKey: ['unrepresented-files'], queryFn: getUnrepresentedFiles })
  const [open, setOpen] = useState(false)
  const [diffPath, setDiffPath] = useState<string | null>(null)

  if (!data || data.files.length === 0) {
    return null
  }
  const noChanges = data.representedFileCount === 0
  const listVisible = open || noChanges

  return (
    <section aria-label="Analysis coverage" className="border-b border-amber-200 bg-amber-50 px-6 py-2 text-sm text-ink-900">
      {noChanges ? (
        <p role="status" className="font-medium">
          No semantic changes were detected in {data.changedFileCount} changed files. Review them as raw diffs below.
        </p>
      ) : (
        <button
          type="button"
          aria-expanded={open}
          onClick={() => setOpen((current) => !current)}
          className="font-medium underline-offset-2 hover:underline"
        >
          {data.files.length} of {data.changedFileCount} changed files are not represented in Athena&apos;s analysis
        </button>
      )}
      {listVisible && (
        <ul aria-label="Unrepresented files" className="mt-2 flex flex-col gap-1">
          {data.files.map((file) => (
            <UnrepresentedFileRow key={file.path} file={file} onOpen={() => setDiffPath(file.path)} />
          ))}
        </ul>
      )}
      {diffPath && <RawDiffDialog path={diffPath} onClose={() => setDiffPath(null)} />}
    </section>
  )
}

function UnrepresentedFileRow({ file, onOpen }: { file: UnrepresentedFile; onOpen: () => void }) {
  return (
    <li className="flex items-baseline gap-3">
      <button type="button" onClick={onOpen} className="font-mono text-xs text-ink-900 underline-offset-2 hover:underline">
        {file.path}
      </button>
      <span className="text-xs text-ink-700">{file.reasonLabel}</span>
    </li>
  )
}

function RawDiffDialog({ path, onClose }: { path: string; onClose: () => void }) {
  const { data, isError } = useQuery({ queryKey: ['raw-diff', path], queryFn: () => getRawDiff(path) })
  return (
    <div role="dialog" aria-label={`Raw diff of ${path}`} className="mt-3 flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <span className="font-mono text-xs font-semibold">{path}</span>
        <SecondaryButton onClick={onClose}>Close</SecondaryButton>
      </div>
      {isError ? <p className="text-xs">Could not load the raw diff.</p> : data && <DiffView diff={data.diff} />}
    </div>
  )
}

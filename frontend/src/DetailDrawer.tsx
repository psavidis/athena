import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addCanvasComment,
  deleteCanvasComment,
  editCanvasComment,
  getCanvasComments,
  getChangeDetail,
  type CanvasComment,
  type ModuleTopology,
  type SemanticDimensionEntry,
} from './api'
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
  | { kind: 'concept'; entry: SemanticDimensionEntry; itemId: string }
  | { kind: 'file'; fileName: string; fromConceptName: string; changeKeys: string[]; itemId: string }
  | { kind: 'overview' }
  | { kind: 'comments'; itemId: string; itemLabel: string }

const KIND_LABEL: Record<DrawerSelection['kind'], string> = {
  concept: 'Concept',
  file: 'File',
  overview: 'PR Overview',
  comments: 'Comments',
}

export default function DetailDrawer({
  selection,
  topology,
  onClose,
  onJumpToFile,
  onOpenComments,
}: {
  selection: DrawerSelection | undefined
  topology: ModuleTopology
  onClose: () => void
  onJumpToFile: (fileName: string) => void
  /** Switches the drawer to this item's comment thread (ticket #134) — the header's
   * own Comment affordance, always present for a concept/file selection (not only
   * once it already has a pin), matching the approved prototype's drawer-comment-btn. */
  onOpenComments: (itemId: string, itemLabel: string) => void
}) {
  if (!selection) {
    return null
  }
  const title =
    selection.kind === 'concept'
      ? selection.entry.conceptName
      : selection.kind === 'file'
        ? selection.fileName
        : selection.kind === 'comments'
          ? selection.itemLabel
          : 'Footprint'
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
        <div className="flex flex-shrink-0 items-center gap-2">
          {(selection.kind === 'concept' || selection.kind === 'file') && (
            <button
              type="button"
              onClick={() => onOpenComments(selection.itemId, title)}
              className="flex items-center gap-1 rounded-lg border border-canvas-gold bg-canvas-gold-soft px-2.5 py-1 text-xs font-semibold text-canvas-gold-deep hover:shadow-[var(--shadow-canvas)]"
            >
              💬 Comment
            </button>
          )}
          <button
            type="button"
            aria-label="Close detail drawer"
            className="rounded-md p-1 text-canvas-ink-faint hover:bg-canvas-line hover:text-canvas-ink"
            onClick={onClose}
          >
            ✕
          </button>
        </div>
      </div>
      <div className="flex-1 overflow-auto px-5 pb-4.5">
        {selection.kind === 'concept' && <ConceptDrawerContent entry={selection.entry} onJumpToFile={onJumpToFile} />}
        {selection.kind === 'file' && <FileDrawerContent selection={selection} />}
        {selection.kind === 'overview' && <OverviewDrawerContent topology={topology} />}
        {selection.kind === 'comments' && <CommentsDrawerContent itemId={selection.itemId} />}
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

/**
 * A canvas item's comment thread (ticket #134): list with author/timestamp,
 * inline edit (Save/Cancel) and delete for each comment, and a Post/Cancel
 * editor for a new one. Athena connects one GitHub account per session (no
 * multi-viewer auth), so every comment posted through this session carries
 * that same author — there is no distinct "someone else's comment" case to
 * hide Edit/Delete behind, unlike the prototype's simulated multi-author
 * thread.
 */
function CommentsDrawerContent({ itemId }: { itemId: string }) {
  const queryClient = useQueryClient()
  const queryKey = ['canvas-comments', itemId]
  const { data: comments, isLoading } = useQuery({
    queryKey,
    queryFn: () => getCanvasComments(itemId),
    retry: false,
  })
  const [draft, setDraft] = useState('')
  const [editingId, setEditingId] = useState<string | undefined>(undefined)
  const [editDraft, setEditDraft] = useState('')

  // Every mutation also invalidates the whole-PR comment-counts query (ticket
  // #134): that query backs both the topbar total and every pin badge on the
  // canvas, none of which share this drawer's own per-item query cache entry.
  const invalidateCounts = () => queryClient.invalidateQueries({ queryKey: ['canvas-comment-counts'] })

  const postMutation = useMutation({
    mutationFn: (text: string) => addCanvasComment(itemId, text),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKey, updated)
      invalidateCounts()
      setDraft('')
    },
  })
  const editMutation = useMutation({
    mutationFn: ({ commentId, text }: { commentId: string; text: string }) => editCanvasComment(itemId, commentId, text),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKey, updated)
      setEditingId(undefined)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (commentId: string) => deleteCanvasComment(itemId, commentId),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKey, updated)
      invalidateCounts()
    },
  })

  if (isLoading) {
    return <LoadingState />
  }

  return (
    <div className="flex flex-col gap-3.5">
      {comments && comments.length === 0 && (
        <p className="text-[13px] text-canvas-ink-faint">No comments yet on this item.</p>
      )}
      {comments && comments.length > 0 && (
        <div className="flex flex-col gap-2.5">
          {comments.map((comment) =>
            editingId === comment.id ? (
              <EditingCommentEntry
                key={comment.id}
                initialText={editDraft}
                onChangeText={setEditDraft}
                onCancel={() => setEditingId(undefined)}
                onSave={() => editMutation.mutate({ commentId: comment.id, text: editDraft })}
              />
            ) : (
              <CommentEntry
                key={comment.id}
                comment={comment}
                onEdit={() => {
                  setEditingId(comment.id)
                  setEditDraft(comment.text)
                }}
                onDelete={() => deleteMutation.mutate(comment.id)}
              />
            ),
          )}
        </div>
      )}
      <div className="rounded-lg border border-canvas-line-strong bg-canvas-paper">
        <textarea
          aria-label="New comment"
          placeholder="Leave a note here…"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          className="min-h-16 w-full resize-y border-none bg-transparent px-3.5 py-3 text-[13px] text-canvas-ink outline-none"
        />
        <div className="flex justify-end gap-2 border-t border-canvas-line px-3.5 py-2">
          <button
            type="button"
            onClick={() => setDraft('')}
            className="rounded-lg px-2.5 py-1.5 text-xs font-semibold text-canvas-ink-soft hover:bg-canvas-line"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={draft.trim().length === 0}
            onClick={() => postMutation.mutate(draft)}
            className="rounded-lg bg-canvas-gold-deep px-3 py-1.5 text-xs font-semibold text-canvas-paper-raised disabled:cursor-not-allowed disabled:opacity-40"
          >
            Post comment
          </button>
        </div>
      </div>
    </div>
  )
}

function CommentEntry({
  comment,
  onEdit,
  onDelete,
}: {
  comment: CanvasComment
  onEdit: () => void
  onDelete: () => void
}) {
  return (
    <div data-testid="comment-entry" className="rounded-lg border border-canvas-line bg-canvas-paper px-3 py-2.5">
      <div className="mb-1 flex items-baseline gap-2">
        <span className="text-xs font-semibold text-canvas-ink">{comment.author}</span>
        <span className="text-[11px] text-canvas-ink-faint">{formatTimestamp(comment.postedAt)}</span>
      </div>
      <p className="mb-2 text-[13px] leading-relaxed text-canvas-ink-soft">{comment.text}</p>
      <div className="flex gap-2">
        <button type="button" onClick={onEdit} className="text-[11.5px] font-semibold text-canvas-ink-soft hover:text-canvas-gold-deep">
          Edit
        </button>
        <button type="button" onClick={onDelete} className="text-[11.5px] font-semibold text-canvas-ink-soft hover:text-canvas-brick">
          Delete
        </button>
      </div>
    </div>
  )
}

function EditingCommentEntry({
  initialText,
  onChangeText,
  onCancel,
  onSave,
}: {
  initialText: string
  onChangeText: (text: string) => void
  onCancel: () => void
  onSave: () => void
}) {
  return (
    <div className="rounded-lg border border-canvas-line-strong bg-canvas-paper px-3 py-2.5">
      <textarea
        aria-label="Edit comment"
        value={initialText}
        onChange={(e) => onChangeText(e.target.value)}
        className="mb-2 min-h-14 w-full resize-y rounded-md border border-canvas-line bg-canvas-paper-raised px-2.5 py-2 text-[13px] text-canvas-ink outline-none"
      />
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="text-[11.5px] font-semibold text-canvas-ink-soft hover:text-canvas-ink">
          Cancel
        </button>
        <button type="button" onClick={onSave} className="text-[11.5px] font-semibold text-canvas-gold-deep">
          Save
        </button>
      </div>
    </div>
  )
}

function formatTimestamp(postedAt: string): string {
  const date = new Date(postedAt)
  return Number.isNaN(date.getTime()) ? postedAt : date.toLocaleString()
}

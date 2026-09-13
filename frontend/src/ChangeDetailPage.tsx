import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { addComment, addPrivateNote, getChangeDetail, type TransformationKind } from './api'
import { BackLink, Card, ErrorState, LoadingState, PageShell, PrimaryButton, SectionLabel } from './ui'

const KIND_LABELS: Record<TransformationKind, string> = {
  RENAME_SYMBOL: 'Rename',
  MOVE_SYMBOL: 'Move',
  ADD_SYMBOL: 'Add',
  REMOVE_SYMBOL: 'Remove',
  CHANGE_METHOD_SIGNATURE: 'Signature change',
  EXTRACT_METHOD: 'Extract method',
  MECHANICAL_REPLACEMENT: 'Mechanical rename',
  FORMATTING_ONLY: 'Formatting',
  RENAME_CLASS: 'Rename class',
  MOVE_CLASS: 'Move class',
  ADD_CLASS: 'Add class',
  REMOVE_CLASS: 'Remove class',
  RENAME_FIELD: 'Rename field',
  MOVE_FIELD: 'Move field',
  ADD_FIELD: 'Add field',
  REMOVE_FIELD: 'Remove field',
}

const CATEGORY_LABELS = {
  BEHAVIORAL: 'Behavioral',
  STRUCTURAL: 'Structural',
  MECHANICAL: 'Mechanical',
  UNKNOWN: 'Unknown',
} as const

export default function ChangeDetailPage({ changeKey, onBack }: { changeKey: string; onBack: () => void }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['change-detail', changeKey],
    queryFn: () => getChangeDetail(changeKey),
    retry: false,
  })

  const [comments, setComments] = useState<string[]>([])
  const [privateNotes, setPrivateNotes] = useState<string[]>([])
  const queryClient = useQueryClient()

  const commentMutation = useMutation({
    mutationFn: (text: string) => addComment({ type: 'CHANGE', changeKey }, text),
    onSuccess: (annotations) => setComments(annotations.comments),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['change-detail', changeKey] }),
  })
  const noteMutation = useMutation({
    mutationFn: (text: string) => addPrivateNote({ type: 'CHANGE', changeKey }, text),
    onSuccess: (annotations) => setPrivateNotes(annotations.privateNotes),
  })

  if (isError) {
    return <ErrorState message="Could not load this Change." />
  }

  if (isLoading || !data) {
    return <LoadingState />
  }

  return (
    <PageShell wide>
      <BackLink onClick={onBack}>← Back to Change Map</BackLink>

      <div className="mb-8">
        <p className="mb-1.5 text-xs font-medium tracking-wide text-ink-500 uppercase">Change</p>
        <h1 className="mb-1.5 text-2xl font-semibold text-ink-900">{data.description}</h1>
        <p className="text-sm text-ink-500">
          <span>{KIND_LABELS[data.kind]}</span>
          <span className="mx-1.5 text-ink-300">·</span>
          <span>{CATEGORY_LABELS[data.category]}</span>
        </p>
      </div>

      <div className="mb-8 grid gap-6 sm:grid-cols-2">
        <Section title="Impact — Symbols">
          <ul className="space-y-1 text-sm text-ink-700">
            {data.symbols.map((symbol) => (
              <li key={symbol} className="font-mono text-xs text-ink-700">
                {symbol}
              </li>
            ))}
          </ul>
        </Section>

        <Section title="Impact — Files">
          <ul className="space-y-1 text-sm text-ink-700">
            {data.files.map((file) => (
              <li key={file} className="font-mono text-xs text-ink-700">
                {file}
              </li>
            ))}
          </ul>
        </Section>
      </div>

      <Section title="Diff">
        <DiffView diff={data.diff} />
      </Section>

      <AnnotationForm
        label="Add a comment"
        placeholder="This will sync to GitHub when you submit your review."
        onSubmit={(text) => commentMutation.mutate(text)}
        isPending={commentMutation.isPending}
        error={commentMutation.isError}
      />
      <AnnotationList items={comments} />

      <AnnotationForm
        label="Add a private note"
        placeholder="Never synced to GitHub — visible only to you."
        onSubmit={(text) => noteMutation.mutate(text)}
        isPending={noteMutation.isPending}
        error={noteMutation.isError}
        variant="private"
      />
      <AnnotationList items={privateNotes} variant="private" />
    </PageShell>
  )
}

function DiffView({ diff }: { diff: string }) {
  if (!diff) {
    return (
      <Card className="overflow-hidden bg-ink-900">
        <pre className="overflow-x-auto p-4 font-mono text-xs leading-relaxed text-ink-100">
          No diff recorded for this Change.
        </pre>
      </Card>
    )
  }

  return (
    <Card className="overflow-hidden bg-ink-900">
      <pre className="overflow-x-auto p-4 font-mono text-xs leading-relaxed">
        {diff.split('\n').map((line, i) => {
          const isAdded = line.startsWith('+')
          const isRemoved = line.startsWith('-')
          const color = isAdded ? 'text-emerald-400' : isRemoved ? 'text-red-400' : 'text-ink-400'
          return (
            <div key={i} className={color}>
              {line || ' '}
            </div>
          )
        })}
      </pre>
    </Card>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-6">
      <SectionLabel>{title}</SectionLabel>
      {children}
    </section>
  )
}

function AnnotationForm({
  label,
  placeholder,
  onSubmit,
  isPending,
  error,
  variant = 'comment',
}: {
  label: string
  placeholder: string
  onSubmit: (text: string) => void
  isPending: boolean
  error: boolean
  variant?: 'comment' | 'private'
}) {
  const [text, setText] = useState('')
  const isBlank = text.trim().length === 0
  const inputId = `annotation-${variant}`

  return (
    <form
      className="mb-2 flex flex-col gap-2"
      onSubmit={(e) => {
        e.preventDefault()
        if (isBlank) {
          return
        }
        onSubmit(text)
        setText('')
      }}
    >
      <label htmlFor={inputId} className="text-sm text-ink-700">
        {label}
      </label>
      <textarea
        id={inputId}
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={placeholder}
        rows={2}
        className={
          'rounded-lg border px-3 py-2 text-sm focus:outline-none ' +
          (variant === 'private'
            ? 'border-amber-300 bg-amber-50 focus:border-amber-500'
            : 'border-ink-200 focus:border-accent')
        }
      />
      <PrimaryButton type="submit" disabled={isPending || isBlank} className="self-start">
        {isPending ? 'Submitting…' : 'Submit'}
      </PrimaryButton>
      {error && <p className="text-sm text-red-600">Could not submit — please try again.</p>}
    </form>
  )
}

function AnnotationList({ items, variant = 'comment' }: { items: string[]; variant?: 'comment' | 'private' }) {
  if (items.length === 0) {
    return null
  }
  return (
    <ul className="mb-6 space-y-1">
      {items.map((item, i) => (
        <li
          key={i}
          className={
            'animate-rise-in rounded-lg px-3 py-2 text-sm ' +
            (variant === 'private' ? 'border border-amber-300 bg-amber-50 text-amber-900' : 'bg-ink-100 text-ink-700')
          }
        >
          {item}
        </li>
      ))}
    </ul>
  )
}

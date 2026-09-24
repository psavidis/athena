import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { addComment, addPrivateNote, getChangeDetail, type TransformationKind } from './api'
import { DiffView, ErrorState, LoadingState, PageShell, PrimaryButton, SecondaryButton, SectionLabel } from './ui'
import { KEY_BINDINGS } from './keyboardBindings'

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
  ADD_CONSTRUCTOR_PARAMETER: 'Add constructor parameter',
  CHANGE_FIELD_ANNOTATIONS: 'Field annotations',
  ADD_ENUM_CONSTANT: 'Add enum constant',
  REMOVE_ENUM_CONSTANT: 'Remove enum constant',
  ADD_ANNOTATION_ELEMENT: 'Add annotation element',
  REMOVE_ANNOTATION_ELEMENT: 'Remove annotation element',
  CHANGE_ANNOTATION_ELEMENT_DEFAULT: 'Annotation default change',
  CHANGE_FIELD_TYPE: 'Field type change',
  CHANGE_PARAMETER_ANNOTATIONS: 'Parameter annotations',
  CHANGE_METHOD_ANNOTATIONS: 'Method annotations',
  CHANGE_CONTROL_FLOW: 'Control flow change',
  MODIFY_METHOD_BODY: 'Body modified',
  PULL_UP_FIELD: 'Pull up field',
  PULL_UP_SYMBOL: 'Pull up',
  CHANGE_MODIFIERS: 'Modifiers',
}

const CATEGORY_LABELS = {
  BEHAVIORAL: 'Behavioral',
  STRUCTURAL: 'Structural',
  MECHANICAL: 'Mechanical',
  UNKNOWN: 'Unknown',
} as const

/** `onBack` returns to the Semantic Change Explorer for this same Change (ticket #91's
 * mode toggle) — never to the retired Change Map. */
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
    <PageShell>
      <div className="mb-2 flex justify-end">
        <SecondaryButton onClick={onBack}>Semantic Explorer</SecondaryButton>
      </div>

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
              <li
                key={file}
                data-testid="touched-file"
                tabIndex={0}
                className="rounded font-mono text-xs text-ink-700 outline-none focus:ring-2 focus:ring-inset focus:ring-accent"
                onKeyDown={(e) => {
                  if (e.key !== KEY_BINDINGS.nextItem) {
                    return
                  }
                  const items = Array.from(document.querySelectorAll<HTMLElement>('[data-testid="touched-file"]'))
                  const index = items.indexOf(e.currentTarget)
                  items[index + 1]?.focus()
                }}
              >
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

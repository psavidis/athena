import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { addComment, addPrivateNote, getChangeDetail } from './api'

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
    return <p className="mx-auto max-w-2xl px-4 py-12 text-red-600">Could not load this Change.</p>
  }

  if (isLoading || !data) {
    return <p className="mx-auto max-w-2xl px-4 py-12 text-neutral-500">Loading…</p>
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <button className="mb-4 text-sm text-neutral-500 hover:underline" onClick={onBack}>
        ← Back to Change Map
      </button>

      <h1 className="mb-1 text-2xl font-semibold text-neutral-900">{data.description}</h1>
      <p className="mb-6 text-sm text-neutral-500">{data.category}</p>

      <Section title="Symbols">
        <ul className="text-sm text-neutral-700">
          {data.symbols.map((symbol) => (
            <li key={symbol}>{symbol}</li>
          ))}
        </ul>
      </Section>

      <Section title="Files">
        <ul className="text-sm text-neutral-700">
          {data.files.map((file) => (
            <li key={file}>{file}</li>
          ))}
        </ul>
      </Section>

      <Section title="Diff">
        <pre className="overflow-x-auto rounded bg-neutral-900 p-3 text-xs text-neutral-100">
          {data.diff || 'No diff recorded for this Change.'}
        </pre>
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
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-6">
      <h2 className="mb-2 text-sm font-medium text-neutral-500">{title}</h2>
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
      <label htmlFor={inputId} className="text-sm text-neutral-600">
        {label}
      </label>
      <textarea
        id={inputId}
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={placeholder}
        className={
          'rounded border px-3 py-2 text-sm focus:outline-none ' +
          (variant === 'private'
            ? 'border-amber-300 bg-amber-50 focus:border-amber-500'
            : 'border-neutral-300 focus:border-neutral-500')
        }
      />
      <button
        type="submit"
        disabled={isPending || isBlank}
        className="self-start rounded bg-neutral-900 px-3 py-1.5 text-sm text-white disabled:opacity-40"
      >
        {isPending ? 'Submitting…' : 'Submit'}
      </button>
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
            'rounded px-3 py-2 text-sm ' +
            (variant === 'private' ? 'border border-amber-300 bg-amber-50 text-amber-900' : 'bg-neutral-100 text-neutral-700')
          }
        >
          {item}
        </li>
      ))}
    </ul>
  )
}

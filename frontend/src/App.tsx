import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { connect, listOpenPullRequests, listRepositories, selectPullRequest } from './api'
import type { ImportedPullRequest } from './api'

export default function App() {
  const [connected, setConnected] = useState(false)
  const [selectedRepo, setSelectedRepo] = useState<string | null>(null)
  const [selectedPr, setSelectedPr] = useState<ImportedPullRequest | null>(null)

  if (!connected) {
    return <ConnectStep onConnected={() => setConnected(true)} />
  }
  if (selectedPr) {
    return <SelectedPrStep pullRequest={selectedPr} />
  }
  if (selectedRepo) {
    return (
      <PullRequestStep
        repositoryFullName={selectedRepo}
        onBack={() => setSelectedRepo(null)}
        onSelected={setSelectedPr}
      />
    )
  }
  return <RepositoryStep onSelected={setSelectedRepo} />
}

function Shell({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <h1 className="mb-6 text-2xl font-semibold text-neutral-900">{title}</h1>
      {children}
    </div>
  )
}

function ConnectStep({ onConnected }: { onConnected: () => void }) {
  const [token, setToken] = useState('')
  const mutation = useMutation({
    mutationFn: connect,
    onSuccess: onConnected,
  })

  return (
    <Shell title="Connect to GitHub">
      <form
        className="flex flex-col gap-3"
        onSubmit={(e) => {
          e.preventDefault()
          mutation.mutate(token)
        }}
      >
        <label className="text-sm text-neutral-600" htmlFor="token">
          Personal Access Token
        </label>
        <input
          id="token"
          type="password"
          autoComplete="off"
          value={token}
          onChange={(e) => setToken(e.target.value)}
          className="rounded border border-neutral-300 px-3 py-2 focus:border-neutral-500 focus:outline-none"
          placeholder="ghp_..."
        />
        <button
          type="submit"
          disabled={mutation.isPending || token.length === 0}
          className="rounded bg-neutral-900 px-4 py-2 text-white disabled:opacity-40"
        >
          {mutation.isPending ? 'Connecting…' : 'Connect'}
        </button>
        {mutation.isError && (
          <p className="text-sm text-red-600">{(mutation.error as Error).message}</p>
        )}
      </form>
    </Shell>
  )
}

function RepositoryStep({ onSelected }: { onSelected: (repositoryFullName: string) => void }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['repositories'],
    queryFn: listRepositories,
  })

  return (
    <Shell title="Select a repository">
      {isLoading && <p className="text-neutral-500">Loading…</p>}
      {isError && <p className="text-red-600">Could not load repositories.</p>}
      <ul className="divide-y divide-neutral-200 rounded border border-neutral-200">
        {data?.map((repo) => (
          <li key={repo.fullName}>
            <button
              className="w-full px-4 py-2 text-left hover:bg-neutral-100"
              onClick={() => onSelected(repo.fullName)}
            >
              {repo.fullName}
            </button>
          </li>
        ))}
      </ul>
    </Shell>
  )
}

function PullRequestStep({
  repositoryFullName,
  onBack,
  onSelected,
}: {
  repositoryFullName: string
  onBack: () => void
  onSelected: (pr: ImportedPullRequest) => void
}) {
  const queryClient = useQueryClient()
  const { data, isLoading, isError } = useQuery({
    queryKey: ['pulls', repositoryFullName],
    queryFn: () => listOpenPullRequests(repositoryFullName),
  })
  const mutation = useMutation({
    mutationFn: (number: number) => selectPullRequest(repositoryFullName, number),
    onSuccess: onSelected,
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['pulls', repositoryFullName] }),
  })

  return (
    <Shell title={`Open PRs — ${repositoryFullName}`}>
      <button className="mb-4 text-sm text-neutral-500 hover:underline" onClick={onBack}>
        ← Back to repositories
      </button>
      {isLoading && <p className="text-neutral-500">Loading…</p>}
      {isError && <p className="text-red-600">Could not load pull requests.</p>}
      {data?.length === 0 && <p className="text-neutral-500">No open pull requests.</p>}
      <ul className="divide-y divide-neutral-200 rounded border border-neutral-200">
        {data?.map((pr) => (
          <li key={pr.number}>
            <button
              className="w-full px-4 py-2 text-left hover:bg-neutral-100 disabled:opacity-40"
              disabled={mutation.isPending}
              onClick={() => mutation.mutate(pr.number)}
            >
              #{pr.number} {pr.title}
            </button>
          </li>
        ))}
      </ul>
      {mutation.isError && (
        <p className="mt-3 text-sm text-red-600">{(mutation.error as Error).message}</p>
      )}
    </Shell>
  )
}

function SelectedPrStep({ pullRequest }: { pullRequest: ImportedPullRequest }) {
  return (
    <Shell title={`#${pullRequest.number} ${pullRequest.title}`}>
      <dl className="space-y-1 text-sm text-neutral-700">
        <div>
          <dt className="inline font-medium">Author: </dt>
          <dd className="inline">{pullRequest.author}</dd>
        </div>
        <div>
          <dt className="inline font-medium">Base: </dt>
          <dd className="inline font-mono">{pullRequest.baseRevision}</dd>
        </div>
        <div>
          <dt className="inline font-medium">Head: </dt>
          <dd className="inline font-mono">{pullRequest.headRevision}</dd>
        </div>
      </dl>
      <p className="mt-6 text-sm text-neutral-500">
        Base and head revisions are checked out and ready for the Change Map (next up).
      </p>
    </Shell>
  )
}

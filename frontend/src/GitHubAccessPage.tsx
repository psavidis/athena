import { useMutation, useQuery } from '@tanstack/react-query'
import { getGitHubConnectUrl, getGitHubStatus, listRepositories } from './api'
import { BackLink, Card, ErrorState, LoadingState, PageHeading, PageShell, PrimaryButton } from './ui'

/**
 * GitHub Access settings page (ticket #113/#145): connection status, the
 * connected account, the currently-accessible repositories (read-only —
 * reflects the GitHub App installation's own access), and a link out to
 * GitHub's own "Configure access" settings for adding/removing repository
 * access. Athena does not implement its own add/remove-access UI — access
 * itself is the installation's own scope, this page only surfaces it.
 *
 * Access-related errors (e.g. the repository list request failing) are
 * shown here, not on the main repository/PR browsing flow (`App.tsx`),
 * which no longer gates behind a blocking "Connect to GitHub" screen.
 */
export default function GitHubAccessPage({ onBack }: { onBack: () => void }) {
  const { data: status, isLoading: statusLoading, isError: statusError } = useQuery({
    queryKey: ['github-status'],
    queryFn: getGitHubStatus,
  })
  const {
    data: repositories,
    isLoading: repositoriesLoading,
    isError: repositoriesError,
  } = useQuery({
    queryKey: ['repositories'],
    queryFn: listRepositories,
    enabled: status?.connected === true,
    retry: false,
  })
  const connectMutation = useMutation({
    mutationFn: getGitHubConnectUrl,
    onSuccess: ({ url }) => {
      window.location.href = url
    },
  })

  return (
    <PageShell>
      <div className="max-w-xl">
        <BackLink onClick={onBack}>← Back</BackLink>
        <PageHeading title="GitHub Access" />

        {statusLoading && <LoadingState />}
        {statusError && <ErrorState message="Could not check GitHub connection status." />}

        {status && (
          <div className="flex flex-col gap-6">
            <Card className="p-4">
              <div className="mb-1 flex items-center gap-2">
                <span
                  data-testid="connection-status-dot"
                  className={`h-2 w-2 rounded-full ${status.connected ? 'bg-green-500' : 'bg-ink-300'}`}
                />
                <span className="text-sm font-medium text-ink-900">
                  {status.connected ? 'Connected' : 'Not connected'}
                </span>
              </div>
              {status.connected && status.accountLogin && (
                <p className="text-sm text-ink-700">Connected as {status.accountLogin}</p>
              )}
              {!status.connected && (
                <div className="mt-3">
                  <PrimaryButton disabled={connectMutation.isPending} onClick={() => connectMutation.mutate()}>
                    {connectMutation.isPending ? 'Redirecting…' : 'Connect GitHub'}
                  </PrimaryButton>
                </div>
              )}
              {status.connected && status.installationConfigureUrl && (
                <a
                  href={status.installationConfigureUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-3 inline-block text-sm text-accent hover:underline"
                >
                  Configure access on GitHub →
                </a>
              )}
            </Card>

            {status.connected && (
              <div>
                <p className="mb-2 text-xs font-medium tracking-wide text-ink-500 uppercase">
                  Accessible repositories
                </p>
                {repositoriesLoading && <LoadingState />}
                {repositoriesError && <ErrorState message="Could not load accessible repositories." />}
                {repositories && repositories.length === 0 && (
                  <p className="text-sm text-ink-500">No repositories accessible yet.</p>
                )}
                {repositories && repositories.length > 0 && (
                  <Card className="divide-y divide-ink-200 overflow-hidden">
                    {repositories.map((repo) => (
                      <div key={repo.fullName} className="px-4 py-3 text-sm text-ink-900">
                        {repo.fullName}
                      </div>
                    ))}
                  </Card>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </PageShell>
  )
}

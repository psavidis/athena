import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { connectObsidianVault, disconnectObsidianVault, getKnowledgeStatus } from './api'
import { BackLink, Card, ErrorState, LoadingState, PageHeading, PageShell, PrimaryButton, SecondaryButton } from './ui'

/**
 * Knowledge (Obsidian) settings page (ticket #118): configuring the
 * optional Knowledge Provider integration, in the same External Systems /
 * Integrations area GitHub Access lives in. Absence of a configured
 * provider is a fully normal state, never an error — Athena works fully
 * without one.
 */
export default function KnowledgeSettingsPage({ onBack }: { onBack: () => void }) {
  const queryClient = useQueryClient()
  const { data: status, isLoading, isError } = useQuery({
    queryKey: ['knowledge-status'],
    queryFn: getKnowledgeStatus,
  })
  const [vaultPath, setVaultPath] = useState('')

  const connectMutation = useMutation({
    mutationFn: connectObsidianVault,
    onSuccess: (newStatus) => queryClient.setQueryData(['knowledge-status'], newStatus),
  })
  const disconnectMutation = useMutation({
    mutationFn: disconnectObsidianVault,
    onSuccess: (newStatus) => queryClient.setQueryData(['knowledge-status'], newStatus),
  })

  return (
    <PageShell>
      <div className="max-w-xl">
        <BackLink onClick={onBack}>← Back</BackLink>
        <PageHeading title="Knowledge" />

        {isLoading && <LoadingState />}
        {isError && <ErrorState message="Could not check the Knowledge Provider status." />}

        {status && (
          <div className="flex flex-col gap-6">
            <Card className="p-4">
              <div className="mb-1 flex items-center gap-2">
                <span
                  data-testid="knowledge-status-dot"
                  className={`h-2 w-2 rounded-full ${status.configured ? 'bg-green-500' : 'bg-ink-300'}`}
                />
                <span className="text-sm font-medium text-ink-900">
                  {status.configured ? 'Configured' : 'Not configured'}
                </span>
              </div>
              {status.configured && status.vaultPath && (
                <p className="text-sm text-ink-700">Obsidian vault: {status.vaultPath}</p>
              )}
              {!status.configured && (
                <div className="mt-3 flex flex-col gap-2">
                  <label htmlFor="vault-path" className="text-sm text-ink-700">
                    Obsidian vault path
                  </label>
                  <input
                    id="vault-path"
                    type="text"
                    value={vaultPath}
                    onChange={(e) => setVaultPath(e.target.value)}
                    placeholder="/path/to/vault"
                    className="rounded border border-ink-200 px-3 py-1.5 text-sm"
                  />
                  <div>
                    <PrimaryButton
                      disabled={connectMutation.isPending || vaultPath.trim() === ''}
                      onClick={() => connectMutation.mutate(vaultPath)}
                    >
                      {connectMutation.isPending ? 'Connecting…' : 'Connect Obsidian'}
                    </PrimaryButton>
                  </div>
                  {connectMutation.isError && (
                    <p className="text-sm text-red-600">Could not connect that vault — check the path exists.</p>
                  )}
                </div>
              )}
              {status.configured && (
                <div className="mt-3">
                  <SecondaryButton disabled={disconnectMutation.isPending} onClick={() => disconnectMutation.mutate()}>
                    {disconnectMutation.isPending ? 'Disconnecting…' : 'Disconnect'}
                  </SecondaryButton>
                </div>
              )}
            </Card>
          </div>
        )}
      </div>
    </PageShell>
  )
}

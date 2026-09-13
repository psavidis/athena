import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticChangeExplorerPage from './SemanticChangeExplorerPage'
import type { SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_change_explorer_flow_and_architecture.feature

function renderExplorer(changeKey = 'test-change-key') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onBack = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticChangeExplorerPage scope={{ kind: 'change', changeKey }} onBack={onBack} />
    </QueryClientProvider>,
  )
  return { onBack }
}

function mockSemanticProfile(changeKey: string, profile: SemanticProfile) {
  server.use(http.get(`/api/review/change-map/${changeKey}/semantic-profile`, () => HttpResponse.json(profile)))
}

async function selectLevel(name: string) {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name }))
  return user
}

const FLOW_CLASSIFIED: SemanticProfile = {
  dimensions: [
    {
      dimension: 'FEATURE',
      conceptName: 'Add User',
      conceptDescription: 'The flow for creating a new user account.',
      inferred: false,
      confidencePercent: 100,
      evidence: ['+ class UserRegistrationService { }'],
      supportingConceptNames: [],
    },
  ],
}

const NO_FLOW: SemanticProfile = { dimensions: [] }

const ARCHITECTURE_SINGLE: SemanticProfile = {
  dimensions: [
    {
      dimension: 'ARCHITECTURE',
      conceptName: 'Driving Adapter',
      conceptDescription: 'An adapter that invokes the application through an inbound port.',
      inferred: true,
      confidencePercent: 70,
      evidence: ['+ class UserController { }'],
      supportingConceptNames: [],
    },
  ],
}

const ARCHITECTURE_TWO: SemanticProfile = {
  dimensions: [
    {
      dimension: 'ARCHITECTURE',
      conceptName: 'Driving Adapter',
      conceptDescription: 'An adapter that invokes the application through an inbound port.',
      inferred: true,
      confidencePercent: 70,
      evidence: ['+ class UserController { }'],
      supportingConceptNames: [],
    },
    {
      dimension: 'ARCHITECTURE',
      conceptName: 'Domain Service',
      conceptDescription: "Domain logic that doesn't naturally belong to a single entity or value object.",
      inferred: true,
      confidencePercent: 70,
      evidence: ['+ class UserService { }'],
      supportingConceptNames: [],
    },
  ],
}

const NO_ARCHITECTURE: SemanticProfile = { dimensions: [] }

describe('Semantic Change Explorer — Flow and Architecture level views', () => {
  it('shows the affected flow', async () => {
    // Given ... whose Flow level is classified as "Add User"
    mockSemanticProfile('test-change-key', FLOW_CLASSIFIED)
    renderExplorer()

    // When the reviewer selects the Flow level on the spine
    await selectLevel('Flow')

    // Then the center stage shows "Add User" as the affected flow
    expect(screen.getByRole('heading', { name: 'Add User' })).toBeVisible()
  })

  it('shows Flow classifications as always Observed', async () => {
    // Given ... whose Flow level is classified as "Add User"
    mockSemanticProfile('test-change-key', FLOW_CLASSIFIED)
    renderExplorer()

    // When the reviewer selects the Flow level on the spine
    await selectLevel('Flow')

    // Then the "Add User" flow shows an Observed confidence at 100%
    expect(screen.getByText('Observed · 100%')).toBeVisible()
  })

  it('shows an empty state when the Change has no flow classification', async () => {
    // Given ... whose Flow level has no classification
    mockSemanticProfile('test-change-key', NO_FLOW)
    renderExplorer()

    // When the reviewer selects the Flow level on the spine
    await selectLevel('Flow')

    // Then the center stage shows that the Flow level has no classification for this Change
    expect(await screen.findByText('No Flow classification for this Change yet.')).toBeVisible()
  })

  it('shows the architectural role the Change touches, highlighted', async () => {
    // Given ... whose Architecture level is classified as touching the "Driving Adapter" role
    mockSemanticProfile('test-change-key', ARCHITECTURE_SINGLE)
    renderExplorer()

    // When the reviewer selects the Architecture level on the spine
    await selectLevel('Architecture')

    // Then the "Driving Adapter" role is shown highlighted
    const list = screen.getByRole('list', { name: 'Architectural roles' })
    const role = within(list).getByText('Driving Adapter').closest('li')!
    expect(role).toHaveAttribute('aria-current', 'true')
  })

  it('shows more than one role when the Change touches more than one', async () => {
    // Given ... whose Architecture level is classified as touching both "Driving Adapter" and "Domain Service"
    mockSemanticProfile('test-change-key', ARCHITECTURE_TWO)
    renderExplorer()

    // When the reviewer selects the Architecture level on the spine
    await selectLevel('Architecture')

    // Then both roles are shown highlighted
    const list = screen.getByRole('list', { name: 'Architectural roles' })
    expect(within(list).getByText('Driving Adapter').closest('li')).toHaveAttribute('aria-current', 'true')
    expect(within(list).getByText('Domain Service').closest('li')).toHaveAttribute('aria-current', 'true')
  })

  it('shows only the roles present in the classification, not the full set of recognized roles', async () => {
    // Given ... whose Architecture level is classified as touching only the "Driving Adapter" role
    mockSemanticProfile('test-change-key', ARCHITECTURE_SINGLE)
    renderExplorer()

    // When the reviewer selects the Architecture level on the spine
    await selectLevel('Architecture')

    // Then the center stage shows only the "Driving Adapter" role
    const list = screen.getByRole('list', { name: 'Architectural roles' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(1)
  })

  it('shows the architecture role\'s confidence as Inferred with a percentage', async () => {
    // Given ... whose Architecture level is classified as touching "Driving Adapter" with 70% confidence
    mockSemanticProfile('test-change-key', ARCHITECTURE_SINGLE)
    renderExplorer()

    // When the reviewer selects the Architecture level on the spine
    await selectLevel('Architecture')

    // Then the "Driving Adapter" role shows an Inferred confidence at 70%
    expect(screen.getByText('Inferred · 70%')).toBeVisible()
  })

  it('shows an empty state when the Change has no architectural classification', async () => {
    // Given ... whose Architecture level has no classification
    mockSemanticProfile('test-change-key', NO_ARCHITECTURE)
    renderExplorer()

    // When the reviewer selects the Architecture level on the spine
    await selectLevel('Architecture')

    // Then the center stage shows that the Architecture level has no classification for this Change
    expect(await screen.findByText('No Architecture classification for this Change yet.')).toBeVisible()
  })
})

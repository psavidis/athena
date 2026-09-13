import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticChangeExplorerPage from './SemanticChangeExplorerPage'
import type { SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_change_explorer_framework_and_capability.feature

function renderExplorer(changeKey = 'test-change-key') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onBack = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticChangeExplorerPage changeKey={changeKey} onBack={onBack} />
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

const FRAMEWORK_MECHANISM: SemanticProfile = {
  dimensions: [
    {
      dimension: 'FRAMEWORK',
      conceptName: 'Spring Constructor Injection',
      conceptDescription: 'A collaborator is supplied through the constructor rather than a framework-injected field.',
      inferred: false,
      confidencePercent: 100,
      evidence: ['+ public UserService(UserRepository userRepository) { this.userRepository = userRepository; }'],
      supportingConceptNames: [],
      beforeEvidenceCount: 0,
    },
  ],
}

const FRAMEWORK_TRANSITION: SemanticProfile = {
  dimensions: [
    {
      dimension: 'FRAMEWORK',
      conceptName: 'Spring: Field to Constructor Injection',
      conceptDescription: 'A Spring-managed dependency moved from an @Autowired field to a constructor parameter.',
      inferred: false,
      confidencePercent: 100,
      evidence: [
        '@Autowired\n- private UserRepository userRepository;',
        '+ public UserService(UserRepository userRepository) { this.userRepository = userRepository; }',
      ],
      supportingConceptNames: [],
      beforeEvidenceCount: 1,
    },
  ],
}

const FRAMEWORK_NO_PRIOR_MECHANISM: SemanticProfile = {
  dimensions: [
    {
      dimension: 'FRAMEWORK',
      conceptName: 'JPA Entity Mapping',
      conceptDescription: 'A JPA relationship mapping between entities.',
      inferred: false,
      confidencePercent: 100,
      evidence: ['+ @OneToMany\nprivate List<Item> items;'],
      supportingConceptNames: [],
      beforeEvidenceCount: 0,
    },
  ],
}

const NO_FRAMEWORK: SemanticProfile = { dimensions: [] }

const CAPABILITY_SINGLE: SemanticProfile = {
  dimensions: [
    {
      dimension: 'RESPONSIBILITY',
      conceptName: 'Process Payment',
      conceptDescription: 'A new business-meaningful capability was introduced.',
      inferred: true,
      confidencePercent: 82,
      evidence: ['+ public void processPayment() { }'],
      supportingConceptNames: [],
      beforeEvidenceCount: 0,
    },
  ],
}

const CAPABILITY_TWO: SemanticProfile = {
  dimensions: [
    {
      dimension: 'RESPONSIBILITY',
      conceptName: 'Process Payment',
      conceptDescription: 'A new business-meaningful capability was introduced.',
      inferred: true,
      confidencePercent: 82,
      evidence: ['+ public void processPayment() { }'],
      supportingConceptNames: [],
      beforeEvidenceCount: 0,
    },
    {
      dimension: 'RESPONSIBILITY',
      conceptName: 'Validate Card',
      conceptDescription: 'A new business-meaningful capability was introduced.',
      inferred: true,
      confidencePercent: 82,
      evidence: ['+ public boolean validateCard() { }'],
      supportingConceptNames: [],
      beforeEvidenceCount: 0,
    },
  ],
}

const NO_CAPABILITY: SemanticProfile = { dimensions: [] }

describe('Semantic Change Explorer — Framework and Capability level views', () => {
  it('shows the identified framework mechanism', async () => {
    // Given ... whose Framework level is classified as "Spring Constructor Injection"
    mockSemanticProfile('test-change-key', FRAMEWORK_MECHANISM)
    renderExplorer()

    // When the reviewer selects the Framework level on the spine
    await selectLevel('Framework')

    // Then the center stage shows "Spring Constructor Injection" as the framework mechanism
    expect(screen.getByRole('heading', { name: 'Spring Constructor Injection' })).toBeVisible()
  })

  it('shows an explicit before/after for a Framework mechanism transition', async () => {
    // Given ... whose Framework level is classified as a transition from "@Autowired field injection" to "constructor injection"
    mockSemanticProfile('test-change-key', FRAMEWORK_TRANSITION)
    renderExplorer()

    // When the reviewer selects the Framework level on the spine
    await selectLevel('Framework')

    // Then the center stage shows the before mechanism and the after mechanism
    expect(screen.getByText('Before')).toBeVisible()
    expect(screen.getByText('After')).toBeVisible()
    expect(screen.getAllByText(/@Autowired/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/this\.userRepository = userRepository/).length).toBeGreaterThan(0)
  })

  it('shows only the current mechanism, without a before/after split, when there is no prior mechanism', async () => {
    // Given ... whose Framework level is classified as "JPA Entity Mapping" with no prior mechanism
    mockSemanticProfile('test-change-key', FRAMEWORK_NO_PRIOR_MECHANISM)
    renderExplorer()

    // When the reviewer selects the Framework level on the spine
    await selectLevel('Framework')

    // Then the center stage shows "JPA Entity Mapping" as the current mechanism, without a before/after split
    expect(screen.getByRole('heading', { name: 'JPA Entity Mapping' })).toBeVisible()
    expect(screen.queryByText('Before')).not.toBeInTheDocument()
    expect(screen.queryByText('After')).not.toBeInTheDocument()
  })

  it('shows Framework classifications as always Observed', async () => {
    // Given ... whose Framework level is classified as "Jackson Serialization"
    mockSemanticProfile('test-change-key', {
      dimensions: [{ ...FRAMEWORK_MECHANISM.dimensions[0], conceptName: 'Jackson Serialization' }],
    })
    renderExplorer()

    // When the reviewer selects the Framework level on the spine
    await selectLevel('Framework')

    // Then the "Jackson Serialization" mechanism shows an Observed confidence at 100%
    expect(screen.getByText('Observed · 100%')).toBeVisible()
  })

  it('shows an empty state when the Change has no framework classification', async () => {
    // Given ... whose Framework level has no classification
    mockSemanticProfile('test-change-key', NO_FRAMEWORK)
    renderExplorer()

    // When the reviewer selects the Framework level on the spine
    await selectLevel('Framework')

    // Then the center stage shows that the Framework level has no classification for this Change
    expect(await screen.findByText('No Framework classification for this Change yet.')).toBeVisible()
  })

  it('shows the affected capability in human-readable terms', async () => {
    // Given ... whose Capability level is classified as "Process Payment"
    mockSemanticProfile('test-change-key', CAPABILITY_SINGLE)
    renderExplorer()

    // When the reviewer selects the Capability level on the spine
    await selectLevel('Capability')

    // Then the center stage shows "Process Payment" as a capability card
    const grid = screen.getByRole('list', { name: 'Capabilities' })
    expect(within(grid).getByRole('button', { name: /Process Payment/ })).toBeVisible()
  })

  it('shows a capability\'s supporting code evidence when its card is selected', async () => {
    // Given ... whose Capability level is classified as "Process Payment"
    mockSemanticProfile('test-change-key', CAPABILITY_SINGLE)
    renderExplorer()
    await selectLevel('Capability')
    const grid = screen.getByRole('list', { name: 'Capabilities' })

    // When the reviewer selects the "Process Payment" capability card
    await userEvent.setup().click(within(grid).getByRole('button', { name: /Process Payment/ }))

    // Then the evidence panel shows the code evidence for "Process Payment"
    expect(screen.getByRole('group', { name: 'Process Payment' })).toHaveAttribute('aria-current', 'true')
  })

  it('shows separate, individually-selectable cards when the Change touches more than one capability', async () => {
    // Given ... whose Capability level is classified as both "Process Payment" and "Validate Card"
    mockSemanticProfile('test-change-key', CAPABILITY_TWO)
    renderExplorer()

    // When the reviewer selects the Capability level on the spine
    await selectLevel('Capability')

    // Then the center stage shows a card for "Process Payment" and a separate card for "Validate Card"
    const grid = screen.getByRole('list', { name: 'Capabilities' })
    expect(within(grid).getAllByRole('button')).toHaveLength(2)
    expect(within(grid).getByRole('button', { name: /Process Payment/ })).toBeVisible()
    expect(within(grid).getByRole('button', { name: /Validate Card/ })).toBeVisible()
  })

  it('shows a capability\'s confidence as Inferred with a percentage', async () => {
    // Given ... whose Capability level is classified as "Add User" with 82% confidence
    mockSemanticProfile('test-change-key', {
      dimensions: [{ ...CAPABILITY_SINGLE.dimensions[0], conceptName: 'Add User', confidencePercent: 82 }],
    })
    renderExplorer()

    // When the reviewer selects the Capability level on the spine
    await selectLevel('Capability')

    // Then the "Add User" capability card shows an Inferred confidence at 82%
    expect(screen.getByText('Inferred · 82%')).toBeVisible()
  })

  it('shows an empty state when the Change has no responsibility classification', async () => {
    // Given ... whose Capability level has no classification
    mockSemanticProfile('test-change-key', NO_CAPABILITY)
    renderExplorer()

    // When the reviewer selects the Capability level on the spine
    await selectLevel('Capability')

    // Then the center stage shows that the Capability level has no classification for this Change
    expect(await screen.findByText('No Capability classification for this Change yet.')).toBeVisible()
  })
})

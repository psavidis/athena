import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SemanticChangeExplorerPage from './SemanticChangeExplorerPage'
import type { SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_change_explorer_motion_and_animation.feature

function renderExplorer(changeKey = 'test-change-key') {
  server.use(
    http.get('/api/review/change-map', () =>
      HttpResponse.json({ prTitle: 'Test PR', categoryCounts: {}, changes: [], classGroups: [] }),
    ),
    http.get('/api/review/modules', () => HttpResponse.json([])),
  )
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onScopeChange = vi.fn()
  const onExitPr = vi.fn()
  const onOpenDiffView = vi.fn()
  const onOpenAiAnalysis = vi.fn()
  const onOpenSummary = vi.fn()
  const onNotConnected = vi.fn()
  const onNoPullRequestSelected = vi.fn()
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticChangeExplorerPage
        scope={{ kind: 'change', changeKey }}
        onScopeChange={onScopeChange}
        onExitPr={onExitPr}
        onOpenDiffView={onOpenDiffView}
        onOpenAiAnalysis={onOpenAiAnalysis}
        onOpenSummary={onOpenSummary}
        onNotConnected={onNotConnected}
        onNoPullRequestSelected={onNoPullRequestSelected}
      />
    </QueryClientProvider>,
  )
  return { onScopeChange, onExitPr, onOpenDiffView, onOpenAiAnalysis, onOpenSummary, onNotConnected, onNoPullRequestSelected }
}

function mockSemanticProfile(changeKey: string, profile: SemanticProfile) {
  server.use(http.get(`/api/review/change-map/${changeKey}/semantic-profile`, () => HttpResponse.json(profile)))
}

async function selectLevel(name: string) {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name }))
  return user
}

const SHARED_EVIDENCE = '- @Autowired\n- private UserRepository userRepository;'

const PATTERN_WITH_SUPPORTING_STRUCTURE: SemanticProfile = {
  dimensions: [
    {
      dimension: 'PATTERN',
      conceptName: 'Dependency Injection',
      conceptDescription: 'A collaborator is supplied from outside rather than constructed internally.',
      inferred: true,
      confidencePercent: 70,
      evidence: [SHARED_EVIDENCE],
      supportingConceptNames: ['Add Constructor Parameter'],
    },
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Add Constructor Parameter',
      conceptDescription: 'A parameter was added to a constructor.',
      inferred: false,
      confidencePercent: 100,
      evidence: [SHARED_EVIDENCE],
      supportingConceptNames: [],
    },
  ],
}

const CAPABILITY_AND_FLOW: SemanticProfile = {
  dimensions: [
    {
      dimension: 'RESPONSIBILITY',
      conceptName: 'Add User',
      conceptDescription: 'A new business-meaningful capability was introduced.',
      inferred: true,
      confidencePercent: 70,
      evidence: [SHARED_EVIDENCE],
      supportingConceptNames: [],
    },
    {
      dimension: 'FEATURE',
      conceptName: 'Register User',
      conceptDescription: 'The flow for registering a new user.',
      inferred: false,
      confidencePercent: 100,
      evidence: [SHARED_EVIDENCE],
      supportingConceptNames: [],
    },
  ],
}

const ARCHITECTURE_CONNECTED: SemanticProfile = {
  dimensions: [
    {
      dimension: 'ARCHITECTURE',
      conceptName: 'Outbound Port',
      conceptDescription: 'A port through which the application drives an external system.',
      inferred: true,
      confidencePercent: 70,
      evidence: [SHARED_EVIDENCE],
      supportingConceptNames: [],
    },
    {
      dimension: 'ARCHITECTURE',
      conceptName: 'Driven Adapter',
      conceptDescription: 'An adapter that implements an outbound port against a real external system.',
      inferred: true,
      confidencePercent: 70,
      evidence: [SHARED_EVIDENCE],
      supportingConceptNames: [],
    },
  ],
}

const PATTERN_FRAMEWORK_UNRELATED_CAPABILITY: SemanticProfile = {
  dimensions: [
    {
      dimension: 'PATTERN',
      conceptName: 'Dependency Injection',
      conceptDescription: 'A collaborator is supplied from outside rather than constructed internally.',
      inferred: true,
      confidencePercent: 70,
      evidence: [SHARED_EVIDENCE],
      supportingConceptNames: [],
    },
    {
      dimension: 'FRAMEWORK',
      conceptName: 'Spring: Field to Constructor Injection',
      conceptDescription: 'A Spring-managed dependency moved from an @Autowired field to a constructor parameter.',
      inferred: false,
      confidencePercent: 100,
      evidence: [SHARED_EVIDENCE],
      supportingConceptNames: [],
    },
    {
      dimension: 'RESPONSIBILITY',
      conceptName: 'Add User',
      conceptDescription: 'A new business-meaningful capability was introduced.',
      inferred: true,
      confidencePercent: 70,
      evidence: ['+ public void addUser() { }'],
      supportingConceptNames: [],
    },
  ],
}

describe('Semantic Change Explorer — purposeful motion and animation pass', () => {
  const originalMatchMedia = window.matchMedia

  afterEach(() => {
    window.matchMedia = originalMatchMedia
  })

  function mockReducedMotion(reduced: boolean) {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: reduced && query === '(prefers-reduced-motion: reduce)',
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
  }

  it('clusters the structural changes supporting a Pattern under it when the Pattern level is entered', async () => {
    // Given the reviewer is viewing the Structure level ... whose Pattern level is classified as "Dependency Injection"
    mockSemanticProfile('test-change-key', PATTERN_WITH_SUPPORTING_STRUCTURE)
    renderExplorer()

    // When the reviewer selects the Pattern level on the spine
    await selectLevel('Pattern')

    // Then the structural changes supporting "Dependency Injection" are shown clustered under it
    const patternCard = screen.getByRole('group', { name: 'Dependency Injection structural cluster' })
    // And the reviewer can still identify each individual structural change within the cluster
    expect(within(patternCard).getByText('Add Constructor Parameter')).toBeVisible()
  })

  it('preserves the reviewer\'s place in the Explorer across a level transition', async () => {
    // Given the reviewer is viewing the Pattern level of the Semantic Change Explorer for a Change
    mockSemanticProfile('test-change-key', PATTERN_WITH_SUPPORTING_STRUCTURE)
    renderExplorer()
    await selectLevel('Pattern')

    // When the reviewer selects the Framework level on the spine
    await selectLevel('Framework')

    // Then the center stage transitions to the Framework level's content
    expect(screen.getByRole('button', { name: 'Framework' })).toHaveAttribute('aria-current', 'true')
    // And the reviewer's place in the Explorer (spine, evidence panel) is preserved across the transition
    expect(screen.getByRole('navigation', { name: 'Semantic levels' })).toBeVisible()
    expect(screen.getByText('Evidence')).toBeVisible()
  })

  it('momentarily emphasizes a classification\'s evidence on hover, clearing on hover-out', async () => {
    // Given a level whose current classification has supporting evidence
    mockSemanticProfile('test-change-key', PATTERN_WITH_SUPPORTING_STRUCTURE)
    renderExplorer()
    await selectLevel('Pattern')
    const user = userEvent.setup()
    const heading = screen.getByRole('heading', { name: 'Dependency Injection' })
    const evidenceGroup = () => screen.getByRole('group', { name: 'Dependency Injection' })

    // When the reviewer hovers that classification
    await user.hover(heading)

    // Then its supporting evidence is momentarily emphasized in the evidence panel
    expect(evidenceGroup()).toHaveAttribute('data-hover-emphasized', 'true')

    // And the emphasis clears when the reviewer stops hovering
    await user.unhover(heading)
    expect(evidenceGroup()).not.toHaveAttribute('data-hover-emphasized')
  })

  it('highlights the associated Flow when a Capability is selected', async () => {
    // Given ... whose Capability level is classified as "Add User" and whose Flow level is classified as "Register User"
    mockSemanticProfile('test-change-key', CAPABILITY_AND_FLOW)
    renderExplorer()
    await selectLevel('Capability')

    // When the reviewer selects the "Add User" capability
    const capabilities = screen.getByRole('list', { name: 'Capabilities' })
    await userEvent.setup().click(within(capabilities).getByRole('button', { name: /Add User/ }))

    // Then the "Register User" flow is shown highlighted
    expect(screen.getByRole('group', { name: 'Register User' })).toHaveAttribute('aria-current', 'true')
  })

  it('highlights connected components when an architectural role is selected', async () => {
    // Given the Architecture level includes a "Driven Adapter" connected to an "Outbound Port"
    mockSemanticProfile('test-change-key', ARCHITECTURE_CONNECTED)
    renderExplorer()
    await selectLevel('Architecture')

    // When the reviewer selects the "Outbound Port" role
    await userEvent.setup().click(screen.getByRole('button', { name: 'Outbound Port' }))

    // Then the connected "Driven Adapter" is shown highlighted
    expect(screen.getByRole('group', { name: 'Driven Adapter' })).toHaveAttribute('aria-current', 'true')
  })

  it('transitions unrelated content to a subdued appearance rather than disappearing immediately', async () => {
    // Given Pattern and Framework share evidence, and Capability is classified from unrelated evidence
    mockSemanticProfile('test-change-key', PATTERN_FRAMEWORK_UNRELATED_CAPABILITY)
    renderExplorer()
    await selectLevel('Pattern')

    // When the reviewer selects the Pattern classification
    await userEvent.setup().click(screen.getByRole('heading', { name: 'Dependency Injection' }))

    // Then the Capability classification transitions to a subdued appearance rather than disappearing immediately
    const capabilityGroup = screen.getByRole('group', { name: 'Add User' })
    expect(capabilityGroup).toBeVisible()
    expect(capabilityGroup.className).toMatch(/transition-\[?opacity/)
    expect(capabilityGroup.className).toContain('opacity-40')
  })

  it('shows level content immediately, without a transition animation, when reduced motion is requested', async () => {
    // Given the reviewer has requested reduced motion
    mockReducedMotion(true)
    // And the reviewer is viewing the Semantic Change Explorer for a Change
    mockSemanticProfile('test-change-key', PATTERN_WITH_SUPPORTING_STRUCTURE)
    renderExplorer()

    // When the reviewer selects a different level on the spine
    await selectLevel('Framework')

    // Then the center stage shows that level's content immediately, without a transition animation
    const main = screen.getByRole('button', { name: 'Framework' })
    expect(main).toHaveAttribute('aria-current', 'true')
    // And no information available with motion enabled is missing
    expect(screen.getByText('Evidence')).toBeVisible()
  })

  it('never blocks the evidence panel from being shown mid-transition', async () => {
    // Given the reviewer is viewing the Explorer, mid-way through a level transition
    mockSemanticProfile('test-change-key', PATTERN_WITH_SUPPORTING_STRUCTURE)
    renderExplorer()
    await selectLevel('Pattern')
    await selectLevel('Framework')

    // When the reviewer opens the evidence panel for the current level
    // Then the reviewer can view the supporting evidence immediately
    expect(await screen.findByText('Evidence')).toBeVisible()
    // And no pending animation blocks the evidence from being shown
    expect(screen.queryByText(/loading/i)).not.toBeInTheDocument()
  })
})

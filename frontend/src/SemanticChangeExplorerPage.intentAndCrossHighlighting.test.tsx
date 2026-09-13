import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticChangeExplorerPage from './SemanticChangeExplorerPage'
import type { SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_change_explorer_intent_and_cross_highlighting.feature

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

const INTENT_PRIMARY_ONLY: SemanticProfile = {
  dimensions: [
    {
      dimension: 'INTENT',
      conceptName: 'Reduce Coupling',
      conceptDescription: 'The change lowers how much one part of the system depends on another’s internals.',
      inferred: true,
      confidencePercent: 70,
      evidence: ['Dependency is now supplied through the constructor'],
      supportingConceptNames: [],
    },
  ],
}

const INTENT_WITH_ALTERNATIVE: SemanticProfile = {
  dimensions: [
    {
      dimension: 'INTENT',
      conceptName: 'Reduce Coupling',
      conceptDescription: 'The change lowers how much one part of the system depends on another’s internals.',
      inferred: true,
      confidencePercent: 70,
      evidence: ['Dependency is now supplied through the constructor'],
      supportingConceptNames: [],
    },
    {
      dimension: 'INTENT',
      conceptName: 'Improve Maintainability',
      conceptDescription: 'The change makes future changes easier without altering behavior.',
      inferred: true,
      confidencePercent: 50,
      evidence: ['Dependency is now supplied through the constructor'],
      supportingConceptNames: [],
    },
  ],
}

const NO_INTENT: SemanticProfile = { dimensions: [] }

const PATTERN_INFERRED: SemanticProfile = {
  dimensions: [
    {
      dimension: 'PATTERN',
      conceptName: 'Dependency Injection',
      conceptDescription: 'A collaborator is supplied from outside rather than constructed internally.',
      inferred: true,
      confidencePercent: 70,
      evidence: ['- private UserRepository userRepository;\n+ private final UserRepository userRepository;'],
      supportingConceptNames: [],
    },
  ],
}

const SHARED_EVIDENCE = '- @Autowired\n- private UserRepository userRepository;'

function withSharedPatternAndFramework(): SemanticProfile {
  return {
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
    ],
  }
}

function withSharedPatternFrameworkAndUnrelatedCapability(): SemanticProfile {
  const shared = withSharedPatternAndFramework()
  return {
    dimensions: [
      ...shared.dimensions,
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
}

describe('Semantic Change Explorer — Intent level and semantic cross-highlighting', () => {
  it('shows the primary inferred reason with a "Why?" framing', async () => {
    // Given ... whose Intent level is classified as "Reduce Coupling"
    mockSemanticProfile('test-change-key', INTENT_PRIMARY_ONLY)
    renderExplorer()

    // When the reviewer selects the Intent level on the spine
    await selectLevel('Intent')

    // Then the center stage shows "Reduce Coupling" as the primary inferred reason
    expect(screen.getByText('Why?')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Reduce Coupling' })).toBeVisible()
  })

  it('lists the specific evidence backing the inference', async () => {
    // Given ... backed by the evidence "Dependency is now supplied through the constructor"
    mockSemanticProfile('test-change-key', INTENT_PRIMARY_ONLY)
    renderExplorer()

    // When the reviewer selects the Intent level on the spine
    await selectLevel('Intent')

    // Then the center stage lists that evidence
    const list = screen.getByRole('list', { name: 'Reduce Coupling supporting evidence' })
    expect(within(list).getByText('Dependency is now supplied through the constructor')).toBeVisible()
  })

  it('shows an alternative reading distinctly, at a lower confidence than the primary', async () => {
    // Given ... "Reduce Coupling" primary with "Improve Maintainability" as an alternative
    mockSemanticProfile('test-change-key', INTENT_WITH_ALTERNATIVE)
    renderExplorer()

    // When the reviewer selects the Intent level on the spine
    await selectLevel('Intent')

    // Then "Improve Maintainability" is shown as an alternative reading, distinct from the primary
    const alternatives = screen.getByRole('list', { name: 'Alternative readings' })
    expect(within(alternatives).getByText('Improve Maintainability')).toBeVisible()
    // And the alternative reading's confidence is lower than the primary reading's confidence
    expect(within(alternatives).getByText('· 50%')).toBeVisible()
  })

  it('shows an empty state when the Change has no intent classification', async () => {
    // Given ... whose Intent level has no classification
    mockSemanticProfile('test-change-key', NO_INTENT)
    renderExplorer()

    // When the reviewer selects the Intent level on the spine
    await selectLevel('Intent')

    // Then the center stage shows that the Intent level has no classification for this Change
    expect(await screen.findByText('No Intent classification for this Change yet.')).toBeVisible()
  })

  it('exposes a "Show evidence" affordance on an Inferred level (Pattern)', async () => {
    // Given ... a Change with an Inferred classification on the Pattern level
    mockSemanticProfile('test-change-key', PATTERN_INFERRED)
    renderExplorer()

    // When the reviewer selects the Pattern level on the spine
    await selectLevel('Pattern')

    // Then the reviewer can see that level's supporting evidence
    expect(screen.getByRole('group', { name: 'Dependency Injection' })).toBeVisible()
    expect(screen.getByText(/private final UserRepository userRepository/)).toBeVisible()
  })

  it('highlights other classifications that share evidence when a classified concept is selected', async () => {
    // Given ... whose Pattern and Framework levels are both classified from the same underlying evidence
    mockSemanticProfile('test-change-key', withSharedPatternAndFramework())
    renderExplorer()
    await selectLevel('Pattern')

    // When the reviewer selects the Pattern classification
    await userEvent.setup().click(screen.getByRole('heading', { name: 'Dependency Injection' }))

    // Then the Framework classification that shares its evidence is shown highlighted
    expect(screen.getByRole('group', { name: 'Spring: Field to Constructor Injection' })).toHaveAttribute(
      'aria-current',
      'true',
    )
  })

  it('subdues unrelated content rather than removing it', async () => {
    // Given ... Pattern and Framework share evidence, and Capability is classified from unrelated evidence
    mockSemanticProfile('test-change-key', withSharedPatternFrameworkAndUnrelatedCapability())
    renderExplorer()
    await selectLevel('Pattern')

    // When the reviewer selects the Pattern classification
    await userEvent.setup().click(screen.getByRole('heading', { name: 'Dependency Injection' }))

    // Then the Capability classification is shown subdued, not hidden
    const capabilityGroup = screen.getByRole('group', { name: 'Add User' })
    expect(capabilityGroup).toBeVisible()
    expect(capabilityGroup).not.toHaveAttribute('aria-current')
    expect(capabilityGroup.className).toContain('opacity-40')
  })

  it('propagates a real highlight when a bracketed term is clicked in the Change Story', async () => {
    // Given ... whose Pattern and Framework levels are both classified from the same underlying evidence
    mockSemanticProfile('test-change-key', withSharedPatternAndFramework())
    renderExplorer()
    await screen.findByText('Dependency Injection', { selector: 'button' })

    // When the reviewer clicks the Pattern term in the Change Story
    await userEvent.setup().click(screen.getByText('Dependency Injection', { selector: 'button' }))

    // Then the Framework classification that shares its evidence is shown highlighted
    expect(screen.getByRole('group', { name: 'Spring: Field to Constructor Injection' })).toHaveAttribute(
      'aria-current',
      'true',
    )
  })
})

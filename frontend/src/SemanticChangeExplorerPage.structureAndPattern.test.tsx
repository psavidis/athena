import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticChangeExplorerPage from './SemanticChangeExplorerPage'
import type { SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_change_explorer_structure_and_pattern.feature

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

const TWO_STRUCTURAL_CHANGES: SemanticProfile = {
  dimensions: [
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Rename',
      conceptDescription: 'A symbol kept its behavior but changed name.',
      inferred: false,
      confidencePercent: 100,
      evidence: ['- greet()\n+ salute()'],
      supportingConceptNames: [],
    },
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Add',
      conceptDescription: 'A new symbol was introduced with no prior counterpart.',
      inferred: false,
      confidencePercent: 100,
      evidence: ['+ private final UserRepository userRepository;'],
      supportingConceptNames: [],
    },
  ],
}

const NO_STRUCTURAL_CHANGE: SemanticProfile = { dimensions: [] }

function withPatternSupportedBy(supportingConceptNames: string[]): SemanticProfile {
  return {
    dimensions: [
      {
        dimension: 'STRUCTURAL',
        conceptName: 'Add Constructor Parameter',
        conceptDescription: 'A constructor gained a parameter that it assigns straight to a same-named field.',
        inferred: false,
        confidencePercent: 100,
        evidence: ['+ UserService(UserRepository userRepository) { this.userRepository = userRepository; }'],
        supportingConceptNames: [],
      },
      {
        dimension: 'PATTERN',
        conceptName: 'Dependency Injection',
        conceptDescription: 'A collaborator is supplied from outside rather than constructed internally.',
        inferred: true,
        confidencePercent: 70,
        evidence: ['- private UserRepository userRepository;\n+ private final UserRepository userRepository;'],
        supportingConceptNames,
      },
    ],
  }
}

const PATTERN_WITH_CONFIDENCE: SemanticProfile = {
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

const NO_PATTERN: SemanticProfile = {
  dimensions: [
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Rename',
      conceptDescription: 'A symbol kept its behavior but changed name.',
      inferred: false,
      confidencePercent: 100,
      evidence: ['- greet()\n+ salute()'],
      supportingConceptNames: [],
    },
  ],
}

const TWO_PATTERNS: SemanticProfile = {
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
    {
      dimension: 'PATTERN',
      conceptName: 'Factory',
      conceptDescription: 'Object creation is delegated to a dedicated method or type.',
      inferred: true,
      confidencePercent: 70,
      evidence: ['+ class UserFactory { }'],
      supportingConceptNames: [],
    },
  ],
}

async function selectLevel(name: string) {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name }))
  return user
}

describe('Semantic Change Explorer — Structure and Pattern level views', () => {
  it('shows one chip per structural change', async () => {
    // Given ... whose Structure level is classified with the structural changes "Rename" and "Add"
    mockSemanticProfile('test-change-key', TWO_STRUCTURAL_CHANGES)
    renderExplorer()
    await screen.findByRole('list', { name: 'Structural changes' })

    // When the reviewer selects the Structure level on the spine
    await selectLevel('Structure')

    // Then the center stage shows one chip for "Rename" and one chip for "Add"
    const grid = screen.getByRole('list', { name: 'Structural changes' })
    expect(within(grid).getAllByRole('button').map((button) => button.textContent)).toEqual(['Rename', 'Add'])
  })

  it('highlights a structural chip\'s diff evidence when selected', async () => {
    // Given ... whose Structure level is classified with the structural changes "Rename" and "Add"
    mockSemanticProfile('test-change-key', TWO_STRUCTURAL_CHANGES)
    renderExplorer()
    await screen.findByRole('list', { name: 'Structural changes' })
    const user = userEvent.setup()

    // When the reviewer selects the "Rename" chip
    await user.click(screen.getByRole('button', { name: 'Rename' }))

    // Then the evidence panel highlights the diff evidence for "Rename"
    expect(screen.getByRole('group', { name: 'Rename' })).toHaveAttribute('aria-current', 'true')
    // And the diff evidence for "Add" is not highlighted
    expect(screen.getByRole('group', { name: 'Add' })).not.toHaveAttribute('aria-current')
  })

  it('shows structural chips as always Observed', async () => {
    // Given ... whose Structure level is classified with the structural change "Rename"
    mockSemanticProfile('test-change-key', NO_PATTERN)
    renderExplorer()
    await screen.findByRole('list', { name: 'Structural changes' })

    // Then the "Rename" chip shows an Observed confidence at 100%
    const chip = screen.getByRole('button', { name: 'Rename' }).closest('li')!
    expect(within(chip).getByText('Observed · 100%')).toBeVisible()
  })

  it('shows an empty state when the Change has no structural classification', async () => {
    // Given ... whose Structure level has no classification
    mockSemanticProfile('test-change-key', NO_STRUCTURAL_CHANGE)
    renderExplorer()

    // When the reviewer selects the Structure level on the spine
    await selectLevel('Structure')

    // Then the center stage shows that the Structure level has no classification for this Change
    expect(await screen.findByText('No Structure classification for this Change yet.')).toBeVisible()
  })

  it('shows a recognized pattern as a hero card', async () => {
    // Given ... whose Pattern level is classified as "Dependency Injection"
    mockSemanticProfile('test-change-key', PATTERN_WITH_CONFIDENCE)
    renderExplorer()

    // When the reviewer selects the Pattern level on the spine
    await selectLevel('Pattern')

    // Then the center stage shows "Dependency Injection" as a hero card
    expect(screen.getByRole('heading', { name: 'Dependency Injection' })).toBeVisible()
  })

  it('lists the structural changes that support a pattern under its hero card', async () => {
    // Given ... whose Pattern level is classified as "Dependency Injection", supported by "Add Constructor Parameter"
    mockSemanticProfile('test-change-key', withPatternSupportedBy(['Add Constructor Parameter', 'Remove']))
    renderExplorer()
    await selectLevel('Pattern')

    // Then the "Dependency Injection" hero card lists "Add Constructor Parameter" and "Remove" as supporting structural changes
    const supportingList = screen.getByRole('list', { name: 'Dependency Injection supporting structural changes' })
    expect(within(supportingList).getAllByRole('button').map((button) => button.textContent)).toEqual([
      'Add Constructor Parameter',
      'Remove',
    ])
  })

  it('links back to the Structure level when a supporting structural change is selected', async () => {
    // Given ... whose Pattern level is classified as "Dependency Injection", supported by "Add Constructor Parameter"
    mockSemanticProfile('test-change-key', withPatternSupportedBy(['Add Constructor Parameter']))
    renderExplorer()
    await selectLevel('Pattern')
    const user = userEvent.setup()
    const supportingList = screen.getByRole('list', { name: 'Dependency Injection supporting structural changes' })

    // When the reviewer selects "Add Constructor Parameter" under the "Dependency Injection" pattern
    await user.click(within(supportingList).getByRole('button', { name: 'Add Constructor Parameter' }))

    // Then the Structure level is indicated as the current level
    expect(screen.getByRole('button', { name: 'Structure' })).toHaveAttribute('aria-current', 'true')
    // And the center stage highlights the "Add Constructor Parameter" chip
    expect(screen.getByRole('button', { name: 'Add Constructor Parameter' })).toHaveAttribute('aria-current', 'true')
  })

  it('shows a pattern\'s confidence as Inferred with a percentage', async () => {
    // Given ... whose Pattern level is classified as "Dependency Injection" with 70% confidence
    mockSemanticProfile('test-change-key', PATTERN_WITH_CONFIDENCE)
    renderExplorer()
    await selectLevel('Pattern')

    // Then the "Dependency Injection" hero card shows an Inferred confidence at 70%
    expect(screen.getByText('Inferred · 70%')).toBeVisible()
  })

  it('shows an empty state when the Change embodies no recognizable pattern', async () => {
    // Given ... whose Pattern level has no classification
    mockSemanticProfile('test-change-key', NO_PATTERN)
    renderExplorer()

    // When the reviewer selects the Pattern level on the spine
    await selectLevel('Pattern')

    // Then the center stage shows that the Pattern level has no classification for this Change
    expect(screen.getByText('No Pattern classification for this Change yet.')).toBeVisible()
  })

  it('shows one hero card per recognized pattern when there is more than one', async () => {
    // Given ... whose Pattern level is classified as both "Dependency Injection" and "Factory"
    mockSemanticProfile('test-change-key', TWO_PATTERNS)
    renderExplorer()
    await selectLevel('Pattern')

    // Then the center stage shows a hero card for "Dependency Injection" and a hero card for "Factory"
    expect(screen.getAllByRole('heading').map((heading) => heading.textContent)).toEqual([
      'Dependency Injection',
      'Factory',
    ])
  })
})

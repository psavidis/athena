import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import SemanticChangeExplorerPage from './SemanticChangeExplorerPage'
import type { SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_change_explorer_frontend_rendering.feature

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

const RENAME_ONLY: SemanticProfile = {
  dimensions: [
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Rename',
      conceptDescription: 'A symbol (class, method, or field) kept its behavior but changed name.',
      inferred: false,
      confidencePercent: 100,
      evidence: ['- greet()\n+ salute()'],
      supportingConceptNames: [],
    },
  ],
}

const WITH_PATTERN_AND_INTENT: SemanticProfile = {
  dimensions: [
    ...RENAME_ONLY.dimensions,
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
      dimension: 'INTENT',
      conceptName: 'Improve Testability',
      conceptDescription: 'The change makes the code easier to exercise and verify in tests.',
      inferred: true,
      confidencePercent: 70,
      evidence: ['- private UserRepository userRepository;\n+ private final UserRepository userRepository;'],
      supportingConceptNames: [],
    },
  ],
}

const SEVEN_LEVELS = ['Structure', 'Pattern', 'Framework', 'Capability', 'Flow', 'Architecture', 'Intent']

describe('Semantic Change Explorer rendering', () => {
  it('shows all seven spine levels with Structure indicated as the current level', async () => {
    // Given the reviewer is viewing the Semantic Change Explorer for a Change
    mockSemanticProfile('test-change-key', RENAME_ONLY)
    renderExplorer()
    await screen.findByText('Rename')

    // Then the spine shows the levels Structure, Pattern, Framework, Capability, Flow, Architecture, and Intent
    const spine = screen.getByRole('navigation', { name: 'Semantic levels' })
    expect(within(spine).getAllByRole('button').map((button) => button.textContent)).toEqual(SEVEN_LEVELS)
    // And the Structure level is indicated as the current level
    expect(screen.getByRole('button', { name: 'Structure' })).toHaveAttribute('aria-current', 'true')
  })

  it('updates the center stage when a spine level is selected, without leaving the Explorer', async () => {
    // Given ... a Change whose Pattern level is classified as "Dependency Injection"
    mockSemanticProfile('test-change-key', WITH_PATTERN_AND_INTENT)
    const { onBack } = renderExplorer()
    await screen.findByText('Rename')
    const user = userEvent.setup()

    // When the reviewer selects the Pattern level on the spine
    await user.click(screen.getByRole('button', { name: 'Pattern' }))

    // Then the center stage shows the Pattern level's content
    expect(
      screen.getByText('A collaborator is supplied from outside rather than constructed internally.'),
    ).toBeVisible()
    // And the Pattern level is indicated as the current level
    expect(screen.getByRole('button', { name: 'Pattern' })).toHaveAttribute('aria-current', 'true')
    // And the reviewer is still viewing the Semantic Change Explorer
    expect(onBack).not.toHaveBeenCalled()
  })

  it("reflects the Change's actual classifications in the Change Story sentence", async () => {
    // Given ... whose Pattern level is classified as "Dependency Injection" and whose Intent level is classified as "Improve Testability"
    mockSemanticProfile('test-change-key', WITH_PATTERN_AND_INTENT)
    renderExplorer()

    // Then the Change Story includes "Dependency Injection"
    expect(await screen.findByText(/Dependency Injection/)).toBeVisible()
    // And the Change Story includes "Improve Testability"
    expect(screen.getByText(/Improve Testability/)).toBeVisible()
  })

  it('selects the corresponding level and updates the evidence panel when a Change Story element is clicked', async () => {
    // Given ... whose Pattern level is classified as "Dependency Injection"
    mockSemanticProfile('test-change-key', WITH_PATTERN_AND_INTENT)
    renderExplorer()
    await screen.findByText(/Dependency Injection/)
    const user = userEvent.setup()

    // When the reviewer clicks "Dependency Injection" in the Change Story
    await user.click(screen.getByText('Dependency Injection'))

    // Then the Pattern level is indicated as the current level
    expect(screen.getByRole('button', { name: 'Pattern' })).toHaveAttribute('aria-current', 'true')
    // And the evidence panel shows the evidence supporting that classification
    expect(screen.getByText(/private final UserRepository/)).toBeVisible()
  })

  it('shows the underlying diff in the evidence panel for the current level', async () => {
    // Given ... whose Structure level is classified as "Rename"
    mockSemanticProfile('test-change-key', RENAME_ONLY)
    renderExplorer()

    // Then the evidence panel shows the underlying diff for that Change
    expect(await screen.findByText(/salute\(\)/)).toBeVisible()
  })

  it('shows a minimal empty state for a level with no classification, instead of fabricated content', async () => {
    // Given ... whose Flow level has no classification
    mockSemanticProfile('test-change-key', RENAME_ONLY)
    renderExplorer()
    await screen.findByText('Rename')
    const user = userEvent.setup()

    // When the reviewer selects the Flow level on the spine
    await user.click(screen.getByRole('button', { name: 'Flow' }))

    // Then the center stage shows that the Flow level has no classification for this Change
    expect(screen.getByText('No Flow classification for this Change yet.')).toBeVisible()
  })

  it('navigates back to the Change Map', async () => {
    // Given the reviewer is viewing the Semantic Change Explorer for a Change
    mockSemanticProfile('test-change-key', RENAME_ONLY)
    const { onBack } = renderExplorer()
    await screen.findByText('Rename')
    const user = userEvent.setup()

    // When the reviewer navigates back
    await user.click(screen.getByText('← Back to Change Map'))

    // Then the reviewer sees the Change Map again
    expect(onBack).toHaveBeenCalled()
  })
})

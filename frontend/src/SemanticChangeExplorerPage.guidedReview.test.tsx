import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import SemanticChangeExplorerPage from './SemanticChangeExplorerPage'
import type { SemanticProfile } from './api'
import { server } from './test/server'

// Traces frontend/src/test/resources/features/ui_first_experience/semantic_change_explorer_guided_review_and_mode_toggle.feature

function renderExplorer(changeKey = 'test-change-key') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <SemanticChangeExplorerPage changeKey={changeKey} onBack={() => {}} />
    </QueryClientProvider>,
  )
}

function mockSemanticProfile(changeKey: string, profile: SemanticProfile) {
  server.use(http.get(`/api/review/change-map/${changeKey}/semantic-profile`, () => HttpResponse.json(profile)))
}

const FULL_PROFILE: SemanticProfile = {
  dimensions: [
    {
      dimension: 'STRUCTURAL',
      conceptName: 'Rename',
      conceptDescription: 'A symbol was renamed.',
      inferred: false,
      confidencePercent: 100,
      evidence: ['- greet()\n+ salute()'],
      supportingConceptNames: [],
    },
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
      dimension: 'FRAMEWORK',
      conceptName: 'Spring: Field to Constructor Injection',
      conceptDescription: 'A Spring-managed dependency moved from an @Autowired field to a constructor parameter.',
      inferred: false,
      confidencePercent: 100,
      evidence: ['- @Autowired'],
      supportingConceptNames: [],
    },
    {
      dimension: 'RESPONSIBILITY',
      conceptName: 'Billing',
      conceptDescription: 'Handles invoicing and payment capture for a customer.',
      inferred: true,
      confidencePercent: 80,
      evidence: ['+ public void chargeCustomer() { }'],
      supportingConceptNames: [],
    },
    {
      dimension: 'FEATURE',
      conceptName: 'Checkout',
      conceptDescription: 'The flow that takes a customer from cart to confirmed order.',
      inferred: true,
      confidencePercent: 75,
      evidence: ['+ public void chargeCustomer() { }'],
      supportingConceptNames: [],
    },
    {
      dimension: 'ARCHITECTURE',
      conceptName: 'Application Service',
      conceptDescription: 'Coordinates a use case across domain and infrastructure.',
      inferred: false,
      confidencePercent: 100,
      evidence: ['+ class BillingService'],
      supportingConceptNames: [],
    },
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

async function startGuidedReview() {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Start guided review' }))
  return user
}

describe('Semantic Change Explorer — Guided Review mode', () => {
  it('shows the first chapter when guided review is started', async () => {
    // Given the reviewer is viewing the Semantic Change Explorer for a Change
    mockSemanticProfile('test-change-key', FULL_PROFILE)
    renderExplorer()

    // When the reviewer starts guided review
    await startGuidedReview()

    // Then the reviewer sees the "Understand the change" chapter, showing Structure
    expect(screen.getByRole('heading', { name: 'Understand the change' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Rename' })).toBeVisible()
  })

  it('advances to the next chapter, pairing Pattern and Framework, when the reviewer continues', async () => {
    // Given the reviewer is in guided review, viewing the "Understand the change" chapter
    mockSemanticProfile('test-change-key', FULL_PROFILE)
    renderExplorer()
    const user = await startGuidedReview()

    // When the reviewer continues
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    // Then the reviewer sees the "Understand the implementation" chapter with both levels
    expect(screen.getByRole('heading', { name: 'Understand the implementation' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Dependency Injection' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Spring: Field to Constructor Injection' })).toBeVisible()
  })

  it('shows both levels of a paired chapter together', async () => {
    // Given the reviewer is in guided review, viewing the "Understand the affected behavior" chapter
    mockSemanticProfile('test-change-key', FULL_PROFILE)
    renderExplorer()
    const user = await startGuidedReview()
    await user.click(screen.getByRole('button', { name: 'Continue' })) // -> implementation
    await user.click(screen.getByRole('button', { name: 'Continue' })) // -> affected behavior

    // Then the chapter shows Capability ("Billing") and Flow ("Checkout") content together
    expect(screen.getByRole('heading', { name: 'Understand the affected behavior' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Billing' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Checkout' })).toBeVisible()
  })

  it('returns to the previous chapter when the reviewer goes back', async () => {
    // Given the reviewer is in guided review, viewing the "Understand the implementation" chapter
    mockSemanticProfile('test-change-key', FULL_PROFILE)
    renderExplorer()
    const user = await startGuidedReview()
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    // When the reviewer goes back
    await user.click(screen.getByRole('button', { name: 'Back' }))

    // Then the reviewer sees the "Understand the change" chapter again
    expect(screen.getByRole('heading', { name: 'Understand the change' })).toBeVisible()
  })

  it('shows the reviewer\'s current position and total chapter count on the rail', async () => {
    // Given the reviewer is in guided review, viewing the "Understand the affected behavior" chapter
    mockSemanticProfile('test-change-key', FULL_PROFILE)
    renderExplorer()
    const user = await startGuidedReview()
    await user.click(screen.getByRole('button', { name: 'Continue' }))
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    // Then the chapter rail shows the reviewer is on chapter 3 of 6
    expect(screen.getByText('Chapter 3 of 6')).toBeVisible()
  })

  it('offers no continue action on the last chapter', async () => {
    // Given the reviewer is in guided review, viewing the last chapter, "Inspect the evidence"
    mockSemanticProfile('test-change-key', FULL_PROFILE)
    renderExplorer()
    const user = await startGuidedReview()
    for (let i = 0; i < 5; i++) {
      await user.click(screen.getByRole('button', { name: 'Continue' }))
    }

    // Then no continue action is offered
    expect(screen.getByRole('heading', { name: 'Inspect the evidence' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Continue' })).not.toBeInTheDocument()
  })

  it('offers no back action on the first chapter', async () => {
    // Given the reviewer is in guided review, viewing the first chapter, "Understand the change"
    mockSemanticProfile('test-change-key', FULL_PROFILE)
    renderExplorer()
    await startGuidedReview()

    // Then no back action is offered
    expect(screen.queryByRole('button', { name: 'Back' })).not.toBeInTheDocument()
  })

  it("returns to the normal Explorer at the reviewer's prior position when guided review is exited", async () => {
    // Given the reviewer was viewing the Pattern level before starting guided review
    mockSemanticProfile('test-change-key', FULL_PROFILE)
    renderExplorer()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Pattern' }))

    // And the reviewer is in guided review, viewing the "Understand the affected behavior" chapter
    await user.click(screen.getByRole('button', { name: 'Start guided review' }))
    await user.click(screen.getByRole('button', { name: 'Continue' }))
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    // When the reviewer exits guided review
    await user.click(screen.getByRole('button', { name: 'Exit guided review' }))

    // Then the reviewer sees the normal Explorer, one level at a time, back on Pattern
    expect(screen.queryByRole('heading', { name: 'Understand the affected behavior' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pattern' })).toHaveAttribute('aria-current', 'true')
  })
})

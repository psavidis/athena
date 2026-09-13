import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import IntentLevel from './IntentLevel'
import type { SemanticDimensionEntry } from './api'

// Dedicated unit tests for the Intent level's "Why?" panel (ticket #99),
// complementing the black-box coverage in
// SemanticChangeExplorerPage.intentAndCrossHighlighting.test.tsx.

function entry(conceptName: string, overrides: Partial<SemanticDimensionEntry> = {}): SemanticDimensionEntry {
  return {
    dimension: 'INTENT',
    conceptName,
    conceptDescription: `${conceptName} description`,
    inferred: true,
    confidencePercent: 70,
    evidence: [`- old ${conceptName}\n+ new ${conceptName}`],
    supportingConceptNames: [],
    ...overrides,
  }
}

describe('IntentLevel', () => {
  it('shows the primary inferred reason with a "Why?" framing', () => {
    render(<IntentLevel entries={[entry('Reduce Coupling')]} />)

    expect(screen.getByText('Why?')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Reduce Coupling' })).toBeVisible()
  })

  it('lists the specific evidence backing the inference', () => {
    render(
      <IntentLevel
        entries={[entry('Reduce Coupling', { evidence: ['Dependency is now supplied through the constructor'] })]}
      />,
    )

    const list = screen.getByRole('list', { name: 'Reduce Coupling supporting evidence' })
    expect(within(list).getByText('Dependency is now supplied through the constructor')).toBeVisible()
  })

  it('shows an alternative reading distinctly from the primary, at a lower confidence', () => {
    render(
      <IntentLevel
        entries={[
          entry('Reduce Coupling', { confidencePercent: 70 }),
          entry('Improve Maintainability', { confidencePercent: 50 }),
        ]}
      />,
    )

    // The primary is a heading; the alternative reads as a plain chip, not a heading.
    expect(screen.getByRole('heading', { name: 'Reduce Coupling' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Improve Maintainability' })).not.toBeInTheDocument()
    const alternatives = screen.getByRole('list', { name: 'Alternative readings' })
    expect(within(alternatives).getByText('Improve Maintainability')).toBeVisible()
    expect(within(alternatives).getByText('· 50%')).toBeVisible()
  })

  it('does not show an alternative-readings list when there is only a primary reading', () => {
    render(<IntentLevel entries={[entry('Reduce Coupling')]} />)

    expect(screen.queryByRole('list', { name: 'Alternative readings' })).not.toBeInTheDocument()
  })

  it('shows a minimal empty state when the Change has no intent classification', () => {
    render(<IntentLevel entries={[]} />)

    expect(screen.getByText('No Intent classification for this Change yet.')).toBeVisible()
    expect(screen.queryByRole('heading', { level: 3 })).not.toBeInTheDocument()
  })
})

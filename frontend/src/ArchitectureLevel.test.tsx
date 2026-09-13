import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import ArchitectureLevel from './ArchitectureLevel'
import type { SemanticDimensionEntry } from './api'

// Dedicated unit tests for the Architecture level's layered roles stack
// (ticket #98), complementing the black-box coverage in
// SemanticChangeExplorerPage.flowAndArchitecture.test.tsx.

function entry(conceptName: string, overrides: Partial<SemanticDimensionEntry> = {}): SemanticDimensionEntry {
  return {
    dimension: 'ARCHITECTURE',
    conceptName,
    conceptDescription: `${conceptName} description`,
    inferred: true,
    confidencePercent: 70,
    evidence: [`+ new ${conceptName}`],
    supportingConceptNames: [],
    ...overrides,
  }
}

describe('ArchitectureLevel', () => {
  it('shows the role the Change touches, highlighted', () => {
    render(<ArchitectureLevel entries={[entry('Driving Adapter')]} />)

    const role = screen.getByText('Driving Adapter').closest('li')!
    expect(role).toHaveAttribute('aria-current', 'true')
  })

  it('shows its confidence as Inferred with a percentage', () => {
    render(<ArchitectureLevel entries={[entry('Driving Adapter', { confidencePercent: 70 })]} />)

    expect(screen.getByText('Inferred · 70%')).toBeVisible()
  })

  it('shows more than one role when the Change touches more than one', () => {
    render(<ArchitectureLevel entries={[entry('Driving Adapter'), entry('Domain Service')]} />)

    const list = screen.getByRole('list', { name: 'Architectural roles' })
    expect(within(list).getByText('Driving Adapter')).toBeVisible()
    expect(within(list).getByText('Domain Service')).toBeVisible()
  })

  it('shows only the roles present in the classification, not a full fixed stack', () => {
    render(<ArchitectureLevel entries={[entry('Driving Adapter')]} />)

    const list = screen.getByRole('list', { name: 'Architectural roles' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(1)
    expect(within(list).queryByText('Driven Adapter')).not.toBeInTheDocument()
  })

  it('shows a minimal empty state when the Change has no architectural classification', () => {
    render(<ArchitectureLevel entries={[]} />)

    expect(screen.getByText('No Architecture classification for this Change yet.')).toBeVisible()
    expect(screen.queryByRole('list', { name: 'Architectural roles' })).not.toBeInTheDocument()
  })
})

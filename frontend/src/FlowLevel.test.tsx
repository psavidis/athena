import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import FlowLevel from './FlowLevel'
import type { SemanticDimensionEntry } from './api'

// Dedicated unit tests for the Flow level's affected-flow panel (ticket
// #98), complementing the black-box coverage in
// SemanticChangeExplorerPage.flowAndArchitecture.test.tsx.

function entry(conceptName: string, overrides: Partial<SemanticDimensionEntry> = {}): SemanticDimensionEntry {
  return {
    dimension: 'FEATURE',
    conceptName,
    conceptDescription: `${conceptName} description`,
    inferred: false,
    confidencePercent: 100,
    evidence: [`+ new ${conceptName}`],
    supportingConceptNames: [],
    ...overrides,
  }
}

describe('FlowLevel', () => {
  it('shows the affected flow', () => {
    render(<FlowLevel entries={[entry('Add User')]} />)

    expect(screen.getByRole('heading', { name: 'Add User' })).toBeVisible()
    expect(screen.getByText('Add User description')).toBeVisible()
  })

  it('always shows the flow as Observed at 100%', () => {
    render(<FlowLevel entries={[entry('Add User', { confidencePercent: 100 })]} />)

    expect(screen.getByText('Observed · 100%')).toBeVisible()
  })

  it('shows a minimal empty state when the Change has no flow classification', () => {
    render(<FlowLevel entries={[]} />)

    expect(screen.getByText('No Flow classification for this Change yet.')).toBeVisible()
    expect(screen.queryByRole('heading', { level: 3 })).not.toBeInTheDocument()
  })
})

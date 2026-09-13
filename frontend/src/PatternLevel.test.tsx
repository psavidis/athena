import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import PatternLevel from './PatternLevel'
import type { SemanticDimensionEntry } from './api'

// Dedicated unit tests for the Pattern level's hero card(s) (ticket #96),
// complementing the black-box coverage in
// SemanticChangeExplorerPage.structureAndPattern.test.tsx.

function entry(conceptName: string, overrides: Partial<SemanticDimensionEntry> = {}): SemanticDimensionEntry {
  return {
    dimension: 'PATTERN',
    conceptName,
    conceptDescription: `${conceptName} description`,
    inferred: true,
    confidencePercent: 70,
    evidence: [`- old ${conceptName}\n+ new ${conceptName}`],
    supportingConceptNames: [],
    ...overrides,
  }
}

describe('PatternLevel', () => {
  it('shows a recognized pattern as a hero card', () => {
    render(<PatternLevel entries={[entry('Dependency Injection')]} onSelectSupporting={vi.fn()} />)

    expect(screen.getByRole('heading', { name: 'Dependency Injection' })).toBeVisible()
    expect(screen.getByText('Dependency Injection description')).toBeVisible()
  })

  it('shows its confidence as Inferred with a percentage', () => {
    render(<PatternLevel entries={[entry('Dependency Injection', { confidencePercent: 70 })]} onSelectSupporting={vi.fn()} />)

    expect(screen.getByText('Inferred · 70%')).toBeVisible()
  })

  it('lists the structural changes that support the pattern', () => {
    render(
      <PatternLevel
        entries={[entry('Dependency Injection', { supportingConceptNames: ['Add Constructor Parameter', 'Remove'] })]}
        onSelectSupporting={vi.fn()}
      />,
    )

    const supportingList = screen.getByRole('list', { name: 'Dependency Injection supporting structural changes' })
    expect(within(supportingList).getAllByRole('button').map((button) => button.textContent)).toEqual([
      'Add Constructor Parameter',
      'Remove',
    ])
  })

  it('calls onSelectSupporting when a supporting structural change is clicked', async () => {
    const onSelectSupporting = vi.fn()
    render(
      <PatternLevel
        entries={[entry('Dependency Injection', { supportingConceptNames: ['Add Constructor Parameter'] })]}
        onSelectSupporting={onSelectSupporting}
      />,
    )
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Add Constructor Parameter' }))

    expect(onSelectSupporting).toHaveBeenCalledWith('Add Constructor Parameter')
  })

  it('does not render a supporting-changes list when there are none', () => {
    render(<PatternLevel entries={[entry('Builder', { supportingConceptNames: [] })]} onSelectSupporting={vi.fn()} />)

    expect(screen.queryByRole('list', { name: 'Builder supporting structural changes' })).not.toBeInTheDocument()
  })

  it('shows one hero card per recognized pattern when there is more than one', () => {
    render(
      <PatternLevel
        entries={[entry('Dependency Injection'), entry('Factory')]}
        onSelectSupporting={vi.fn()}
      />,
    )

    expect(screen.getAllByRole('heading').map((heading) => heading.textContent)).toEqual([
      'Dependency Injection',
      'Factory',
    ])
  })

  it('shows a minimal empty state when the Change embodies no recognizable pattern', () => {
    render(<PatternLevel entries={[]} onSelectSupporting={vi.fn()} />)

    expect(screen.getByText('No Pattern classification for this Change yet.')).toBeVisible()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })
})

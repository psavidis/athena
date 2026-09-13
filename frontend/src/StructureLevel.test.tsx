import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import StructureLevel from './StructureLevel'
import type { SemanticDimensionEntry } from './api'

// Dedicated unit tests for the Structure level's chip grid (ticket #96),
// complementing the black-box coverage in
// SemanticChangeExplorerPage.structureAndPattern.test.tsx.

function entry(conceptName: string, overrides: Partial<SemanticDimensionEntry> = {}): SemanticDimensionEntry {
  return {
    dimension: 'STRUCTURAL',
    conceptName,
    conceptDescription: `${conceptName} description`,
    inferred: false,
    confidencePercent: 100,
    evidence: [`- old ${conceptName}\n+ new ${conceptName}`],
    supportingConceptNames: [],
    ...overrides,
  }
}

describe('StructureLevel', () => {
  it('renders one chip per structural change', () => {
    render(<StructureLevel entries={[entry('Rename'), entry('Add')]} selectedConceptName={undefined} onSelect={vi.fn()} />)

    const grid = screen.getByRole('list', { name: 'Structural changes' })
    expect(within(grid).getAllByRole('button').map((button) => button.textContent)).toEqual(['Rename', 'Add'])
  })

  it('shows every chip as Observed at 100%', () => {
    render(<StructureLevel entries={[entry('Rename')]} selectedConceptName={undefined} onSelect={vi.fn()} />)

    const chip = screen.getByRole('button', { name: 'Rename' }).closest('li')!
    expect(within(chip).getByText('Observed · 100%')).toBeVisible()
  })

  it('marks the selected chip as current and no other', () => {
    render(
      <StructureLevel
        entries={[entry('Rename'), entry('Add')]}
        selectedConceptName="Add"
        onSelect={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: 'Rename' })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('button', { name: 'Add' })).toHaveAttribute('aria-current', 'true')
  })

  it('calls onSelect with the clicked chip\'s concept name', async () => {
    const onSelect = vi.fn()
    render(<StructureLevel entries={[entry('Rename'), entry('Add')]} selectedConceptName={undefined} onSelect={onSelect} />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(onSelect).toHaveBeenCalledWith('Add')
  })

  it('shows a minimal empty state when there are no structural changes', () => {
    render(<StructureLevel entries={[]} selectedConceptName={undefined} onSelect={vi.fn()} />)

    expect(screen.getByText('No Structure classification for this Change yet.')).toBeVisible()
    expect(screen.queryByRole('list', { name: 'Structural changes' })).not.toBeInTheDocument()
  })
})

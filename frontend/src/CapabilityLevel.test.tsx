import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import CapabilityLevel from './CapabilityLevel'
import type { SemanticDimensionEntry } from './api'

// Dedicated unit tests for the Capability level's linked cards grid (ticket
// #97), complementing the black-box coverage in
// SemanticChangeExplorerPage.frameworkAndCapability.test.tsx.

function entry(conceptName: string, overrides: Partial<SemanticDimensionEntry> = {}): SemanticDimensionEntry {
  return {
    dimension: 'RESPONSIBILITY',
    conceptName,
    conceptDescription: `${conceptName} description`,
    inferred: true,
    confidencePercent: 82,
    evidence: [`- old ${conceptName}\n+ new ${conceptName}`],
    supportingConceptNames: [],
    beforeEvidenceCount: 0,
    ...overrides,
  }
}

describe('CapabilityLevel', () => {
  it('shows the affected responsibility in human-readable terms', () => {
    render(<CapabilityLevel entries={[entry('Process Payment')]} selectedConceptName={undefined} onSelect={vi.fn()} />)

    expect(screen.getByRole('button', { name: /Process Payment/ })).toBeVisible()
  })

  it('shows its confidence as Inferred with a percentage', () => {
    render(<CapabilityLevel entries={[entry('Add User', { confidencePercent: 82 })]} selectedConceptName={undefined} onSelect={vi.fn()} />)

    expect(screen.getByText('Inferred · 82%')).toBeVisible()
  })

  it('shows one card per capability when the Change touches more than one', () => {
    render(
      <CapabilityLevel
        entries={[entry('Process Payment'), entry('Validate Card')]}
        selectedConceptName={undefined}
        onSelect={vi.fn()}
      />,
    )

    const grid = screen.getByRole('list', { name: 'Capabilities' })
    expect(grid.querySelectorAll('button')).toHaveLength(2)
    expect(screen.getByRole('button', { name: /Process Payment/ })).toBeVisible()
    expect(screen.getByRole('button', { name: /Validate Card/ })).toBeVisible()
  })

  it('calls onSelect with the clicked card\'s concept name', async () => {
    const onSelect = vi.fn()
    render(
      <CapabilityLevel
        entries={[entry('Process Payment'), entry('Validate Card')]}
        selectedConceptName={undefined}
        onSelect={onSelect}
      />,
    )
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: /Validate Card/ }))

    expect(onSelect).toHaveBeenCalledWith('Validate Card')
  })

  it('marks the selected card as current and no other', () => {
    render(
      <CapabilityLevel
        entries={[entry('Process Payment'), entry('Validate Card')]}
        selectedConceptName="Validate Card"
        onSelect={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: /Process Payment/ })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('button', { name: /Validate Card/ })).toHaveAttribute('aria-current', 'true')
  })

  it('shows a minimal empty state when the Change has no responsibility classification', () => {
    render(<CapabilityLevel entries={[]} selectedConceptName={undefined} onSelect={vi.fn()} />)

    expect(screen.getByText('No Capability classification for this Change yet.')).toBeVisible()
    expect(screen.queryByRole('list', { name: 'Capabilities' })).not.toBeInTheDocument()
  })
})

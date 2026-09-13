import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import FrameworkLevel from './FrameworkLevel'
import type { SemanticDimensionEntry } from './api'

// Dedicated unit tests for the Framework level's mechanism panel (ticket #97),
// complementing the black-box coverage in
// SemanticChangeExplorerPage.frameworkAndCapability.test.tsx.

function entry(conceptName: string, overrides: Partial<SemanticDimensionEntry> = {}): SemanticDimensionEntry {
  return {
    dimension: 'FRAMEWORK',
    conceptName,
    conceptDescription: `${conceptName} description`,
    inferred: false,
    confidencePercent: 100,
    evidence: [`- old ${conceptName}\n+ new ${conceptName}`],
    supportingConceptNames: [],
    beforeEvidenceCount: 0,
    ...overrides,
  }
}

describe('FrameworkLevel', () => {
  it('shows the identified mechanism', () => {
    render(<FrameworkLevel entries={[entry('Spring Constructor Injection')]} />)

    expect(screen.getByRole('heading', { name: 'Spring Constructor Injection' })).toBeVisible()
    expect(screen.getByText('Spring Constructor Injection description')).toBeVisible()
  })

  it('always shows the mechanism as Observed at 100%', () => {
    render(<FrameworkLevel entries={[entry('Jackson Serialization', { confidencePercent: 100 })]} />)

    expect(screen.getByText('Observed · 100%')).toBeVisible()
  })

  it('shows an explicit before/after when the classification is a mechanism transition', () => {
    render(
      <FrameworkLevel
        entries={[
          entry('Spring: Field to Constructor Injection', {
            evidence: ['- @Autowired\n- private Repo repo;', '+ public Service(Repo repo) { this.repo = repo; }'],
            beforeEvidenceCount: 1,
          }),
        ]}
      />,
    )

    expect(screen.getByText('Before')).toBeVisible()
    expect(screen.getByText('After')).toBeVisible()
    expect(screen.getByText(/@Autowired/)).toBeVisible()
    expect(screen.getByText(/this\.repo = repo/)).toBeVisible()
  })

  it('shows only the current mechanism, without a before/after split, when there is no prior mechanism', () => {
    render(
      <FrameworkLevel
        entries={[entry('JPA Entity Mapping', { evidence: ['+ @OneToMany'], beforeEvidenceCount: 0 })]}
      />,
    )

    expect(screen.queryByText('Before')).not.toBeInTheDocument()
    expect(screen.queryByText('After')).not.toBeInTheDocument()
    expect(screen.getByText(/@OneToMany/)).toBeVisible()
  })

  it('shows a minimal empty state when the Change has no framework classification', () => {
    render(<FrameworkLevel entries={[]} />)

    expect(screen.getByText('No Framework classification for this Change yet.')).toBeVisible()
    expect(screen.queryByRole('heading', { level: 3 })).not.toBeInTheDocument()
  })
})

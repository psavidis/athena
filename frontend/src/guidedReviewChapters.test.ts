import { describe, expect, it } from 'vitest'
import { GUIDED_REVIEW_CHAPTERS } from './guidedReviewChapters'

// Dedicated unit test for the chapter grouping/sequencing logic (ticket #100's
// Breakdown item 1), independent of how the Explorer renders it.

describe('GUIDED_REVIEW_CHAPTERS', () => {
  it('has six chapters, in the order Structure, Pattern+Framework, Capability+Flow, Architecture, Intent, Evidence', () => {
    expect(GUIDED_REVIEW_CHAPTERS.map((chapter) => chapter.dimensions)).toEqual([
      ['STRUCTURAL'],
      ['PATTERN', 'FRAMEWORK'],
      ['RESPONSIBILITY', 'FEATURE'],
      ['ARCHITECTURE'],
      ['INTENT'],
      [],
    ])
  })

  it('titles each chapter per the ticket\'s naming', () => {
    expect(GUIDED_REVIEW_CHAPTERS.map((chapter) => chapter.title)).toEqual([
      'Understand the change',
      'Understand the implementation',
      'Understand the affected behavior',
      'Understand the architecture',
      'Understand the intent',
      'Inspect the evidence',
    ])
  })

  it('covers every semantic dimension exactly once across the leveled chapters', () => {
    const allDimensions = GUIDED_REVIEW_CHAPTERS.flatMap((chapter) => chapter.dimensions)
    expect(allDimensions.sort()).toEqual(
      ['ARCHITECTURE', 'FEATURE', 'FRAMEWORK', 'INTENT', 'PATTERN', 'RESPONSIBILITY', 'STRUCTURAL'].sort(),
    )
  })
})

import type { SemanticDimension } from './api'

export interface GuidedReviewChapter {
  title: string
  dimensions: SemanticDimension[]
}

/**
 * Guided Review's fixed chapter sequence (ticket #91 §14): the seven-level
 * spine grouped into review chapters in a natural reading order, ending on
 * a dimension-less "evidence" chapter (the underlying diff, not a semantic
 * level). Order and grouping are fixed — not derived from a Change's own
 * classifications — so every reviewer walks the same chapters regardless
 * of what's classified.
 */
export const GUIDED_REVIEW_CHAPTERS: GuidedReviewChapter[] = [
  { title: 'Understand the change', dimensions: ['STRUCTURAL'] },
  { title: 'Understand the implementation', dimensions: ['PATTERN', 'FRAMEWORK'] },
  { title: 'Understand the affected behavior', dimensions: ['RESPONSIBILITY', 'FEATURE'] },
  { title: 'Understand the architecture', dimensions: ['ARCHITECTURE'] },
  { title: 'Understand the intent', dimensions: ['INTENT'] },
  { title: 'Inspect the evidence', dimensions: [] },
]

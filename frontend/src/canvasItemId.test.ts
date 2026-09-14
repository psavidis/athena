import { describe, expect, it } from 'vitest'
import { conceptItemId, fileItemId, territoryItemId } from './canvasItemId'

describe('canvasItemId', () => {
  it('builds a territory id from the module name alone', () => {
    expect(territoryItemId('crowdness-live')).toBe('territory:crowdness-live')
  })

  it('builds a concept id scoped by its owning module', () => {
    expect(conceptItemId('crowdness-live', 'Idempotent recovery')).toBe('concept:crowdness-live:Idempotent recovery')
  })

  it('builds a file id scoped by its owning module', () => {
    expect(fileItemId('crowdness-live', 'PaymentValidator.java')).toBe('file:crowdness-live:PaymentValidator.java')
  })

  it('gives the same file name in two different modules distinct ids', () => {
    expect(fileItemId('crowdness-live', 'Config.java')).not.toBe(fileItemId('crowdness-ingestion', 'Config.java'))
  })
})

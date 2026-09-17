import { describe, expect, it } from 'vitest'
import { groupMomentsByReference, moduleForMoment, nextMomentId, previousMomentId, type Moment } from './reviewReplay'

// Dedicated unit test for the timeline grouping/navigation logic (ticket #211),
// independent of how ReviewReplayTimeline renders it.

function moment(momentId: string, kind: Moment['kind'], reference: string | null): Moment {
  return { momentId, kind, reference, taggedAt: '2026-09-17T10:00:00Z', status: 'CONFIRMED' }
}

describe('groupMomentsByReference', () => {
  it('groups moments that share a reference into one group', () => {
    const question = moment('m1', 'QUESTION', 'entity:OrderService')
    const decision = moment('m2', 'DECISION', 'entity:OrderService')

    const groups = groupMomentsByReference([question, decision])

    expect(groups).toEqual([{ reference: 'entity:OrderService', moments: [question, decision] }])
  })

  it('keeps moments with different references in different groups', () => {
    const question = moment('m1', 'QUESTION', 'entity:OrderService')
    const decision = moment('m2', 'DECISION', 'entity:PaymentService')

    const groups = groupMomentsByReference([question, decision])

    expect(groups).toEqual([
      { reference: 'entity:OrderService', moments: [question] },
      { reference: 'entity:PaymentService', moments: [decision] },
    ])
  })

  it('gives an un-referenced moment its own single-moment group', () => {
    const insight = moment('m1', 'INSIGHT', null)

    const groups = groupMomentsByReference([insight])

    expect(groups).toEqual([{ reference: null, moments: [insight] }])
  })

  it('orders groups by each group\'s first moment', () => {
    const question = moment('m1', 'QUESTION', 'entity:OrderService')
    const unrelated = moment('m2', 'INSIGHT', 'entity:PaymentService')
    const laterDecision = moment('m3', 'DECISION', 'entity:OrderService')

    const groups = groupMomentsByReference([question, unrelated, laterDecision])

    expect(groups.map((group) => group.reference)).toEqual(['entity:OrderService', 'entity:PaymentService'])
    expect(groups[0].moments).toEqual([question, laterDecision])
  })

  it('returns no groups for an empty timeline', () => {
    expect(groupMomentsByReference([])).toEqual([])
  })
})

describe('nextMomentId', () => {
  const moments = [moment('m1', 'QUESTION', null), moment('m2', 'DECISION', null)]

  it('returns the following moment\'s id', () => {
    expect(nextMomentId(moments, 'm1')).toBe('m2')
  })

  it('stays on the last moment when already there', () => {
    expect(nextMomentId(moments, 'm2')).toBe('m2')
  })
})

describe('previousMomentId', () => {
  const moments = [moment('m1', 'QUESTION', null), moment('m2', 'DECISION', null)]

  it('returns the preceding moment\'s id', () => {
    expect(previousMomentId(moments, 'm2')).toBe('m1')
  })

  it('stays on the first moment when already there', () => {
    expect(previousMomentId(moments, 'm1')).toBe('m1')
  })
})

describe('moduleForMoment', () => {
  it('returns the module mapped to the moment\'s reference', () => {
    const question = moment('m1', 'QUESTION', 'entity:OrderService')

    expect(moduleForMoment(question, new Map([['entity:OrderService', 'orders']]))).toBe('orders')
  })

  it('is undefined when the reference has no known module', () => {
    const question = moment('m1', 'QUESTION', 'entity:OrderService')

    expect(moduleForMoment(question, new Map())).toBeUndefined()
  })

  it('is undefined for an un-referenced moment', () => {
    const insight = moment('m1', 'INSIGHT', null)

    expect(moduleForMoment(insight, new Map([['entity:OrderService', 'orders']]))).toBeUndefined()
  })
})

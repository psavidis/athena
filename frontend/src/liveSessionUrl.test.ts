import { describe, expect, it } from 'vitest'
import { liveSessionPath, parseLiveSessionPath } from './liveSessionUrl'

describe('liveSessionUrl', () => {
  it('builds a path from a session id', () => {
    expect(liveSessionPath('abc-123')).toBe('/live/abc-123')
  })

  it('parses a path back into a session id', () => {
    expect(parseLiveSessionPath('/live/abc-123')).toBe('abc-123')
  })

  it('returns null for a path that is not a live session route', () => {
    expect(parseLiveSessionPath('/')).toBeNull()
    expect(parseLiveSessionPath('/repositories/octocat/hello-world/pulls/42')).toBeNull()
  })

  it('round-trips through build then parse', () => {
    const sessionId = 'f47ac10b-58cc-4372-a567-0e02b2c3d479'
    expect(parseLiveSessionPath(liveSessionPath(sessionId))).toBe(sessionId)
  })
})

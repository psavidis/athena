import { describe, expect, it } from 'vitest'
import { parsePullRequestPath, pullRequestPath } from './shareUrl'

describe('shareUrl', () => {
  it('builds a path from a repository full name and PR number', () => {
    expect(pullRequestPath({ repositoryFullName: 'octocat/hello-world', number: 42 })).toBe(
      '/repositories/octocat/hello-world/pulls/42',
    )
  })

  it('parses a path back into a repository full name and PR number', () => {
    expect(parsePullRequestPath('/repositories/octocat/hello-world/pulls/42')).toEqual({
      repositoryFullName: 'octocat/hello-world',
      number: 42,
    })
  })

  it('returns null for a path that is not a PR route', () => {
    expect(parsePullRequestPath('/')).toBeNull()
    expect(parsePullRequestPath('/repositories/octocat/hello-world')).toBeNull()
    expect(parsePullRequestPath('/repositories/octocat/hello-world/pulls/not-a-number')).toBeNull()
  })

  it('round-trips through build then parse', () => {
    const route = { repositoryFullName: 'psavidis/athena', number: 157 }
    expect(parsePullRequestPath(pullRequestPath(route))).toEqual(route)
  })
})

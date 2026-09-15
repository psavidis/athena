import { describe, expect, it } from 'vitest'
import { highlightLine, prismLanguageForPath } from './syntaxHighlight'

describe('prismLanguageForPath', () => {
  it('maps common source extensions to a Prism language id', () => {
    expect(prismLanguageForPath('Foo.java')).toBe('java')
    expect(prismLanguageForPath('component.tsx')).toBe('tsx')
    expect(prismLanguageForPath('module.ts')).toBe('typescript')
    expect(prismLanguageForPath('script.js')).toBe('javascript')
  })

  it('matches the extension case-insensitively', () => {
    expect(prismLanguageForPath('Foo.JAVA')).toBe('java')
  })

  it('resolves the extension from a full path, not just a bare filename', () => {
    expect(prismLanguageForPath('src/main/java/com/athena/Foo.java')).toBe('java')
  })

  it('returns null for an extension with no known grammar', () => {
    expect(prismLanguageForPath('build.gradle.kts')).toBeNull()
  })

  it('returns null for a path with no extension', () => {
    expect(prismLanguageForPath('Makefile')).toBeNull()
  })
})

describe('highlightLine', () => {
  it('tokenizes a Java line into keyword, class-name, and plain-text runs', () => {
    const runs = highlightLine('public class Foo {', 'java')

    const keywordRuns = runs.filter((r) => r.tokenType === 'keyword').map((r) => r.text)
    expect(keywordRuns).toContain('public')
    expect(keywordRuns).toContain('class')

    const classNameRuns = runs.filter((r) => r.tokenType === 'className').map((r) => r.text)
    expect(classNameRuns).toContain('Foo')
  })

  it('tokenizes a string literal as a single string run', () => {
    const runs = highlightLine('String s = "hello";', 'java')

    expect(runs.some((r) => r.tokenType === 'string' && r.text === '"hello"')).toBe(true)
  })

  it('tokenizes a line comment as a comment run', () => {
    const runs = highlightLine('// a note', 'java')

    expect(runs).toEqual([{ text: '// a note', tokenType: 'comment' }])
  })

  it('returns the whole line as one untyped run for an unknown language', () => {
    const runs = highlightLine('public class Foo {', 'cobol')

    expect(runs).toEqual([{ text: 'public class Foo {', tokenType: null }])
  })

  it('returns the whole line as one untyped run when no language is given', () => {
    const runs = highlightLine('anything at all', null)

    expect(runs).toEqual([{ text: 'anything at all', tokenType: null }])
  })

  it('reassembles to the original line text across all runs', () => {
    const line = 'private static final int MAX = 42; // cap'
    const runs = highlightLine(line, 'java')

    expect(runs.map((r) => r.text).join('')).toBe(line)
  })
})

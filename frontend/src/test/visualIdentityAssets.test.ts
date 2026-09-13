/// <reference types="node" />
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// Traces frontend/src/test/resources/features/ui_first_experience/visual_identity.feature
// These scenarios are about static repo assets (the favicon file, the
// README) rather than rendered UI, so they check the files directly
// instead of rendering a component. This is the only test file in the
// suite that needs Node's fs/crypto — a triple-slash reference brings in
// @types/node (already a devDependency, for vite.config.ts) for just this
// file rather than adding "node" to the whole app's tsconfig types, which
// would leak Node globals into browser-facing app code.

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')

// The generic, non-Athena placeholder favicon this ticket replaces (an
// abstract Vite-style mark, not the Athena emblem). Content can't be
// pixel-compared here, so this pins the one concrete, checkable fact: the
// favicon file is no longer exactly this placeholder.
const PLACEHOLDER_FAVICON_SHA256 = '61bc9a161de58248288e6905425d7180f0624c2865007b97d763fdac12043a66'

describe('Athena visual identity — static assets', () => {
  it('serves the Athena logo as the browser tab favicon', () => {
    // Given a user opens the Athena web application
    const indexHtml = readFileSync(resolve(REPO_ROOT, 'frontend/index.html'), 'utf-8')
    const faviconHref = indexHtml.match(/<link rel="icon"[^>]*href="([^"]+)"/)?.[1]
    expect(faviconHref).toBeDefined()

    // Then the browser tab shows the Athena logo as its favicon
    const faviconPath = resolve(REPO_ROOT, 'frontend/public', faviconHref!.replace(/^\//, ''))
    const faviconHash = createHash('sha256').update(readFileSync(faviconPath)).digest('hex')
    expect(faviconHash).not.toBe(PLACEHOLDER_FAVICON_SHA256)
  })

  it('shows the Athena logo near the top of the README', () => {
    // Given a visitor opens the project's README
    const readme = readFileSync(resolve(REPO_ROOT, 'README.md'), 'utf-8')

    // Then the Athena logo appears near the top of the README
    const firstHeadingEnd = readme.indexOf('\n', readme.indexOf('# Athena'))
    const topSection = readme.slice(0, firstHeadingEnd + 400)
    expect(topSection).toMatch(/!\[.*[Ll]ogo.*\]\(.*\)/)
  })

  it("preserves the README's original artwork under an Artwork section near the end", () => {
    // Given a visitor opens the project's README
    const readme = readFileSync(resolve(REPO_ROOT, 'README.md'), 'utf-8')

    // Then the original Athena artwork image is still present, in an "Artwork" section near the end
    const artworkHeadingIndex = readme.indexOf('## Artwork')
    expect(artworkHeadingIndex).toBeGreaterThan(-1)
    expect(readme.slice(artworkHeadingIndex)).toContain('athena.jpeg')
    // "near the end": after every other major section, not just anywhere in the file
    expect(artworkHeadingIndex).toBeGreaterThan(readme.lastIndexOf('## ', artworkHeadingIndex - 1))
  })
})

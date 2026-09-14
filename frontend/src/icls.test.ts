import { describe, expect, it } from 'vitest'
import { parseIclsTheme } from './icls'

describe('parseIclsTheme', () => {
  it('reads name, base colors, and diff colors from a standard .icls scheme', () => {
    const xml = `
      <scheme name="midnight" version="1" parent_scheme="Default">
        <attributes>
          <option name="TEXT">
            <value>
              <option name="FOREGROUND" value="dddddd" />
              <option name="BACKGROUND" value="101010" />
            </value>
          </option>
          <option name="DIFF_INSERTED">
            <value>
              <option name="FOREGROUND" value="00ff00" />
            </value>
          </option>
          <option name="DIFF_DELETED">
            <value>
              <option name="FOREGROUND" value="ff0000" />
            </value>
          </option>
        </attributes>
      </scheme>
    `

    const theme = parseIclsTheme(xml, 'fallback')

    expect(theme).toEqual({
      name: 'midnight',
      background: '#101010',
      foreground: '#dddddd',
      diffInserted: '#00ff00',
      diffDeleted: '#ff0000',
    })
  })

  it('falls back to the given name when the scheme has no name attribute', () => {
    const xml = `<scheme version="1"><attributes /></scheme>`

    const theme = parseIclsTheme(xml, 'unnamed')

    expect(theme.name).toBe('unnamed')
  })

  it('falls back to sensible defaults for colors a scheme omits', () => {
    const xml = `<scheme name="sparse" version="1"><attributes /></scheme>`

    const theme = parseIclsTheme(xml, 'sparse')

    expect(theme.background).toBe('#1c1a17')
    expect(theme.foreground).toBe('#c9c3b8')
    expect(theme.diffInserted).toBe('#34d399')
    expect(theme.diffDeleted).toBe('#f87171')
  })

  it('throws for a document with no <attributes> element', () => {
    const xml = `<scheme name="broken" version="1"></scheme>`

    expect(() => parseIclsTheme(xml, 'broken')).toThrow(/attributes/)
  })
})

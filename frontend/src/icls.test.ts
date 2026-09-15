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

    expect(theme).toMatchObject({
      name: 'midnight',
      background: '#101010',
      foreground: '#dddddd',
      diffInserted: '#00ff00',
      diffDeleted: '#ff0000',
    })
  })

  it('falls back every syntax token color to the base foreground when a scheme defines no syntax colors', () => {
    const xml = `
      <scheme name="midnight" version="1">
        <attributes>
          <option name="TEXT">
            <value><option name="FOREGROUND" value="dddddd" /></value>
          </option>
        </attributes>
      </scheme>
    `

    const theme = parseIclsTheme(xml, 'fallback')

    for (const color of Object.values(theme.syntax)) {
      expect(color).toBe('#dddddd')
    }
  })

  it('reads syntax token colors from their .icls attribute keys', () => {
    const xml = `
      <scheme name="midnight" version="1">
        <attributes>
          <option name="TEXT">
            <value><option name="FOREGROUND" value="dddddd" /></value>
          </option>
          <option name="DEFAULT_KEYWORD">
            <value><option name="FOREGROUND" value="ff79c6" /></value>
          </option>
          <option name="DEFAULT_STRING">
            <value><option name="FOREGROUND" value="f1fa8c" /></value>
          </option>
        </attributes>
      </scheme>
    `

    const theme = parseIclsTheme(xml, 'fallback')

    expect(theme.syntax.keyword).toBe('#ff79c6')
    expect(theme.syntax.string).toBe('#f1fa8c')
    expect(theme.syntax.number).toBe('#dddddd')
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

  it('falls back to DEFAULT_STRING/CONSOLE_ERROR_OUTPUT when a scheme omits DIFF_INSERTED/DIFF_DELETED', () => {
    // A real IntelliJ export (e.g. a scheme whose parent_scheme carries the
    // diff colors by inheritance) commonly omits DIFF_INSERTED/DIFF_DELETED
    // outright rather than repeating the inherited value.
    const xml = `
      <scheme name="inherited" version="1" parent_scheme="Darcula">
        <attributes>
          <option name="TEXT">
            <value>
              <option name="FOREGROUND" value="f8f8f2" />
              <option name="BACKGROUND" value="282a36" />
            </value>
          </option>
          <option name="DEFAULT_STRING">
            <value>
              <option name="FOREGROUND" value="f1fa8c" />
            </value>
          </option>
          <option name="CONSOLE_ERROR_OUTPUT">
            <value>
              <option name="FOREGROUND" value="ff5555" />
            </value>
          </option>
        </attributes>
      </scheme>
    `

    const theme = parseIclsTheme(xml, 'inherited')

    expect(theme.diffInserted).toBe('#f1fa8c')
    expect(theme.diffDeleted).toBe('#ff5555')
  })

  it('prefers DIFF_INSERTED/DIFF_DELETED over the fallback keys when both are present', () => {
    const xml = `
      <scheme name="explicit" version="1">
        <attributes>
          <option name="DIFF_INSERTED">
            <value><option name="FOREGROUND" value="00ff00" /></value>
          </option>
          <option name="DIFF_DELETED">
            <value><option name="FOREGROUND" value="ff0000" /></value>
          </option>
          <option name="DEFAULT_STRING">
            <value><option name="FOREGROUND" value="f1fa8c" /></value>
          </option>
          <option name="CONSOLE_ERROR_OUTPUT">
            <value><option name="FOREGROUND" value="ff5555" /></value>
          </option>
        </attributes>
      </scheme>
    `

    const theme = parseIclsTheme(xml, 'explicit')

    expect(theme.diffInserted).toBe('#00ff00')
    expect(theme.diffDeleted).toBe('#ff0000')
  })
})

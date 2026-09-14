/**
 * Minimal reader for IntelliJ editor color scheme files (.icls) — the same
 * XML format IntelliJ itself exports/imports under Settings > Editor > Color
 * Scheme, so any .icls dropped into diffThemes/ works here unmodified.
 *
 * Only the keys DiffView actually uses are read: TEXT (base fg/bg) and the
 * DIFF_INSERTED / DIFF_DELETED attributes. Everything else in a scheme file
 * (syntax highlighting, console colors, ...) is ignored.
 */
export interface IclsTheme {
  name: string
  background: string
  foreground: string
  diffInserted: string
  diffDeleted: string
}

function hexColor(value: string | null | undefined, fallback: string): string {
  if (!value) return fallback
  return value.startsWith('#') ? value : `#${value}`
}

function findOption(root: Element, name: string): Element | null {
  return root.querySelector(`option[name="${name}"]`)
}

function readColor(attributesEl: Element, optionName: string, key: 'FOREGROUND' | 'BACKGROUND'): string | null {
  const option = findOption(attributesEl, optionName)
  const value = option?.querySelector(':scope > value')
  const colorOption = value?.querySelector(`option[name="${key}"]`)
  return colorOption?.getAttribute('value') ?? null
}

/** Parses raw .icls XML content into the subset of theme data DiffView needs. */
export function parseIclsTheme(xml: string, fallbackName: string): IclsTheme {
  const doc = new DOMParser().parseFromString(xml, 'text/xml')
  const scheme = doc.querySelector('scheme')
  const attributes = doc.querySelector('attributes')

  const name = scheme?.getAttribute('name') || fallbackName

  if (!attributes) {
    throw new Error(`Invalid .icls theme "${fallbackName}": missing <attributes>`)
  }

  return {
    name,
    background: hexColor(readColor(attributes, 'TEXT', 'BACKGROUND'), '#1c1a17'),
    foreground: hexColor(readColor(attributes, 'TEXT', 'FOREGROUND'), '#c9c3b8'),
    diffInserted: hexColor(readColor(attributes, 'DIFF_INSERTED', 'FOREGROUND'), '#34d399'),
    diffDeleted: hexColor(readColor(attributes, 'DIFF_DELETED', 'FOREGROUND'), '#f87171'),
  }
}

/**
 * Minimal reader for IntelliJ editor color scheme files (.icls) — the same
 * XML format IntelliJ itself exports/imports under Settings > Editor > Color
 * Scheme, so any .icls dropped into diffThemes/ works here unmodified.
 *
 * Reads TEXT (base fg/bg), DIFF_INSERTED/DIFF_DELETED, and a syntax palette
 * (`syntax`, keyed by SyntaxTokenType) used to color code tokens the same way
 * IntelliJ's own editor does — keyword, string, class name, etc.
 *
 * What this can't do: IntelliJ's own colors for things like
 * DEFAULT_INSTANCE_FIELD vs. DEFAULT_STATIC_FIELD come from its real
 * type-resolution engine — it knows a given identifier is a field, and
 * whether that field is static, by resolving it against the class that
 * declares it. Highlighting here runs client-side, per diff line, off a
 * regex tokenizer (Prism — see syntaxHighlight.ts) with no such symbol
 * information, so it can only distinguish what's recognizable from a line's
 * text alone (keywords, literals, class names by capitalization, etc.), not
 * field/parameter/local-variable roles. `syntax.field` exists in the palette
 * for a theme that wants to supply one, but nothing currently maps a token
 * to it. (In practice this matters less than it sounds: Dracula.icls itself
 * colors DEFAULT_INSTANCE_FIELD and DEFAULT_STATIC_FIELD the same as plain
 * text, so even real IntelliJ doesn't visually distinguish them there.)
 *
 * A real IntelliJ export often omits DIFF_INSERTED/DIFF_DELETED entirely and
 * relies on `parent_scheme` inheritance for them instead (e.g. Dracula.icls,
 * whose parent is Darcula) — this reader doesn't resolve parent schemes, so
 * for those two colors it falls through to CONSOLE_ERROR_OUTPUT/DEFAULT_STRING,
 * which every real scheme defines directly and which read as a reasonable
 * danger/positive color pair, before finally falling back to Athena's own
 * defaults. The same fallback-to-foreground approach applies to every
 * syntax palette entry a scheme doesn't define.
 */
export type SyntaxTokenType =
  | 'keyword'
  | 'className'
  | 'string'
  | 'number'
  | 'comment'
  | 'function'
  | 'constant'
  | 'parameter'
  | 'annotation'
  | 'operator'
  | 'punctuation'
  | 'field'

export interface IclsTheme {
  name: string
  background: string
  foreground: string
  diffInserted: string
  diffDeleted: string
  syntax: Record<SyntaxTokenType, string>
}

/** Attribute key(s) to try, in order, for each syntax token type. */
const SYNTAX_ATTRIBUTE_KEYS: Record<SyntaxTokenType, string[]> = {
  keyword: ['DEFAULT_KEYWORD'],
  className: ['DEFAULT_CLASS_NAME', 'DEFAULT_CLASS_REFERENCE'],
  string: ['DEFAULT_STRING'],
  number: ['DEFAULT_NUMBER'],
  comment: ['DEFAULT_LINE_COMMENT', 'DEFAULT_BLOCK_COMMENT'],
  function: ['DEFAULT_FUNCTION_DECLARATION', 'DEFAULT_FUNCTION_CALL'],
  constant: ['DEFAULT_CONSTANT'],
  parameter: ['DEFAULT_PARAMETER'],
  annotation: ['DEFAULT_METADATA'],
  operator: ['DEFAULT_OPERATION_SIGN'],
  punctuation: ['DEFAULT_DOT', 'DEFAULT_COMMA', 'DEFAULT_SEMICOLON'],
  // No token produced by the regex tokenizer in syntaxHighlight.ts is ever
  // classified as 'field' (see the file-level comment above) — kept in the
  // palette only so a caller reading `syntax` sees a complete, typed record.
  field: ['DEFAULT_INSTANCE_FIELD', 'DEFAULT_STATIC_FIELD'],
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

function readColorWithFallbacks(attributesEl: Element, optionNames: string[]): string | null {
  for (const optionName of optionNames) {
    const color = readColor(attributesEl, optionName, 'FOREGROUND')
    if (color) return color
  }
  return null
}

function readSyntaxPalette(attributesEl: Element, foreground: string): Record<SyntaxTokenType, string> {
  const entries = Object.entries(SYNTAX_ATTRIBUTE_KEYS) as [SyntaxTokenType, string[]][]
  const palette = {} as Record<SyntaxTokenType, string>
  for (const [tokenType, attributeKeys] of entries) {
    palette[tokenType] = hexColor(readColorWithFallbacks(attributesEl, attributeKeys), foreground)
  }
  return palette
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

  const foreground = hexColor(readColor(attributes, 'TEXT', 'FOREGROUND'), '#c9c3b8')

  return {
    name,
    background: hexColor(readColor(attributes, 'TEXT', 'BACKGROUND'), '#1c1a17'),
    foreground,
    diffInserted: hexColor(readColorWithFallbacks(attributes, ['DIFF_INSERTED', 'DEFAULT_STRING']), '#34d399'),
    diffDeleted: hexColor(readColorWithFallbacks(attributes, ['DIFF_DELETED', 'CONSOLE_ERROR_OUTPUT']), '#f87171'),
    syntax: readSyntaxPalette(attributes, foreground),
  }
}

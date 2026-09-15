/**
 * Turns one line of source into colored token runs for DiffView, using
 * Prism.js as a plain regex tokenizer — not a full compiler. It has no type
 * information, so it recognizes syntax (keywords, string/number literals,
 * things that look like class names, comments, `@Annotation`s) but can't
 * distinguish semantic roles a real language server would (e.g. whether an
 * identifier is an instance field, a static field, or a local variable —
 * see the note in icls.ts). Language is chosen per-line from the enclosing
 * diff's `+++`/`--- ` file header (parsed in DiffView); a line with no
 * detectable language, or in a language we don't ship a grammar for, comes
 * back as a single unhighlighted run.
 */
import Prism from 'prismjs'
import 'prismjs/components/prism-clike'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-javascript'
import 'prismjs/components/prism-typescript'
import 'prismjs/components/prism-markup'
import 'prismjs/components/prism-jsx'
import 'prismjs/components/prism-tsx'
import 'prismjs/components/prism-json'
import 'prismjs/components/prism-yaml'
import type { SyntaxTokenType } from './icls'

const EXTENSION_TO_PRISM_LANGUAGE: Record<string, string> = {
  java: 'java',
  ts: 'typescript',
  tsx: 'tsx',
  js: 'javascript',
  jsx: 'jsx',
  mjs: 'javascript',
  cjs: 'javascript',
  json: 'json',
  yml: 'yaml',
  yaml: 'yaml',
  html: 'markup',
  xml: 'markup',
}

/** Maps a file path's extension to a Prism language id DiffView can highlight with, or null if unsupported/unknown. */
export function prismLanguageForPath(filePath: string): string | null {
  const match = /\.([a-zA-Z0-9]+)$/.exec(filePath)
  if (!match) return null
  return EXTENSION_TO_PRISM_LANGUAGE[match[1].toLowerCase()] ?? null
}

/** One highlighted run within a line: `tokenType` is null for plain text between/outside recognized tokens. */
export interface HighlightedRun {
  text: string
  tokenType: SyntaxTokenType | null
}

const PRISM_TOKEN_TO_SYNTAX_TYPE: Record<string, SyntaxTokenType> = {
  keyword: 'keyword',
  'class-name': 'className',
  string: 'string',
  'template-string': 'string',
  char: 'string',
  number: 'number',
  comment: 'comment',
  function: 'function',
  'function-variable': 'function',
  constant: 'constant',
  parameter: 'parameter',
  annotation: 'annotation',
  decorator: 'annotation',
  operator: 'operator',
  punctuation: 'punctuation',
}

function flatten(tokens: (string | Prism.Token)[], into: HighlightedRun[]): void {
  for (const token of tokens) {
    if (typeof token === 'string') {
      if (token) into.push({ text: token, tokenType: null })
      continue
    }
    const mappedType = PRISM_TOKEN_TO_SYNTAX_TYPE[token.type] ?? null
    const content = token.content
    if (typeof content === 'string') {
      into.push({ text: content, tokenType: mappedType })
    } else if (Array.isArray(content)) {
      // A nested token (e.g. Java's class-name wraps a namespace/punctuation
      // split) — keep the outer semantic type for all of it rather than
      // recursing into the finer breakdown DiffView has no use for.
      into.push({ text: flattenToPlainText(content), tokenType: mappedType })
    } else {
      into.push({ text: String(content), tokenType: mappedType })
    }
  }
}

function flattenToPlainText(tokens: (string | Prism.Token)[]): string {
  return tokens.map((t) => (typeof t === 'string' ? t : flattenToPlainText(Array.isArray(t.content) ? t.content : [t.content as string]))).join('')
}

/** Tokenizes one line of source in the given Prism language into colorable runs; returns the whole line as one plain run for an unsupported/unknown language. */
export function highlightLine(line: string, language: string | null): HighlightedRun[] {
  if (!language || !Prism.languages[language]) {
    return [{ text: line, tokenType: null }]
  }
  const tokens = Prism.tokenize(line, Prism.languages[language])
  const runs: HighlightedRun[] = []
  flatten(tokens, runs)
  return runs
}

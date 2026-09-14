/**
 * Registry of diff-viewer themes, each loaded from a standard IntelliJ
 * .icls color scheme file under diffThemes/. Adding a theme (including one
 * exported from IntelliJ itself) means dropping a new .icls file here and
 * registering it below — DiffView (ui.tsx) never changes.
 */
import athenaIcls from './diffThemes/athena.icls?raw'
import athenaDarkIcls from './diffThemes/athena-dark.icls?raw'
import { parseIclsTheme, type IclsTheme } from './icls'

const registry: Record<string, IclsTheme> = {
  athena: parseIclsTheme(athenaIcls, 'athena'),
  'athena-dark': parseIclsTheme(athenaDarkIcls, 'athena dark'),
}

export const diffViewerThemes: Record<string, IclsTheme> = registry

export const defaultDiffViewerTheme = 'athena'

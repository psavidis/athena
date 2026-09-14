/**
 * The reviewer's chosen diff-viewer theme (diffViewerThemes.ts), persisted
 * per-browser via localStorage — a personal display preference, not
 * something shared with other reviewers or synced to the backend. Every
 * `DiffView` on screen and the theme picker in `AthenaTopBar` each hold
 * their own hook instance, so a change from the picker is broadcast to every
 * other instance via a same-tab `athena:diff-theme-change` CustomEvent
 * carrying the new theme (the browser's own `storage` event only fires in
 * *other* tabs, never the one that made the change).
 */
import { useCallback, useEffect, useState } from 'react'
import { defaultDiffViewerTheme, diffViewerThemes } from './diffViewerThemes'

const STORAGE_KEY = 'athena.diffViewerTheme'
const CHANGE_EVENT = 'athena:diff-theme-change'

function readStoredTheme(): string {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    return stored && stored in diffViewerThemes ? stored : defaultDiffViewerTheme
  } catch {
    return defaultDiffViewerTheme
  }
}

/** Reads and updates the diff-viewer theme preference; stays in sync with every other mounted instance. */
export function useDiffViewerThemePreference(): [string, (theme: string) => void] {
  const [theme, setTheme] = useState(readStoredTheme)

  useEffect(() => {
    function onChange(e: Event) {
      setTheme((e as CustomEvent<string>).detail)
    }
    window.addEventListener(CHANGE_EVENT, onChange)
    return () => window.removeEventListener(CHANGE_EVENT, onChange)
  }, [])

  const selectTheme = useCallback((next: string) => {
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // localStorage unavailable (private browsing, blocked site data) — the
      // selection still applies for the rest of the session via the broadcast below.
    }
    setTheme(next)
    window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: next }))
  }, [])

  return [theme, selectTheme]
}

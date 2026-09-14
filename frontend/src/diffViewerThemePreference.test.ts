import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useDiffViewerThemePreference } from './diffViewerThemePreference'

describe('useDiffViewerThemePreference', () => {
  it('defaults to the athena theme when nothing is stored', () => {
    const { result } = renderHook(() => useDiffViewerThemePreference())

    expect(result.current[0]).toBe('athena')
  })

  it('reflects a selected theme immediately in the selecting instance', () => {
    const { result } = renderHook(() => useDiffViewerThemePreference())

    act(() => result.current[1]('athena-dark'))

    expect(result.current[0]).toBe('athena-dark')
  })

  it('syncs a selection made by one instance to another instance mounted in the same page', () => {
    const picker = renderHook(() => useDiffViewerThemePreference())
    const diffView = renderHook(() => useDiffViewerThemePreference())

    act(() => picker.result.current[1]('athena-dark'))

    expect(diffView.result.current[0]).toBe('athena-dark')
  })

  it('does not affect instances that have already unmounted', () => {
    const picker = renderHook(() => useDiffViewerThemePreference())
    const diffView = renderHook(() => useDiffViewerThemePreference())
    diffView.unmount()

    // Then selecting a theme after unmount doesn't throw despite the
    // now-removed listener.
    expect(() => act(() => picker.result.current[1]('athena-dark'))).not.toThrow()
  })
})

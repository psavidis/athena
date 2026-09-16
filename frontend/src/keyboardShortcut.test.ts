import { describe, expect, it } from 'vitest'
import { isTextEntryTarget, matchesBinding, type ShortcutBinding } from './keyboardShortcut'

// Dedicated unit tests for ticket #159's new keyboardShortcut module — the
// two pure, platform/text-entry-sensitive rules the Technical Requirements
// call out ("Shortcut handling must respect text-entry contexts", "Modifier
// combinations should account for the host platform, particularly macOS"),
// kept independent of any one page's rendering.

describe('isTextEntryTarget', () => {
  it('is true for a textarea', () => {
    const textarea = document.createElement('textarea')
    expect(isTextEntryTarget(textarea)).toBe(true)
  })

  it('is true for a text input', () => {
    const input = document.createElement('input')
    input.type = 'text'
    expect(isTextEntryTarget(input)).toBe(true)
  })

  it('is true for a contenteditable element', () => {
    const div = document.createElement('div')
    div.contentEditable = 'true'
    expect(isTextEntryTarget(div)).toBe(true)
  })

  it('is false for a button', () => {
    const button = document.createElement('button')
    expect(isTextEntryTarget(button)).toBe(false)
  })

  it('is false for null', () => {
    expect(isTextEntryTarget(null)).toBe(false)
  })
})

describe('matchesBinding', () => {
  const binding: ShortcutBinding = { key: 's', modifier: 'primary' }

  it('matches plain Ctrl+key on a non-mac platform', () => {
    const event = new KeyboardEvent('keydown', { key: 's', ctrlKey: true })
    expect(matchesBinding(event, binding, 'other')).toBe(true)
  })

  it('matches Cmd+key on macOS instead of Ctrl+key', () => {
    const event = new KeyboardEvent('keydown', { key: 's', metaKey: true })
    expect(matchesBinding(event, binding, 'mac')).toBe(true)
  })

  it('does not match Ctrl+key on macOS for a primary-modifier binding', () => {
    const event = new KeyboardEvent('keydown', { key: 's', ctrlKey: true })
    expect(matchesBinding(event, binding, 'mac')).toBe(false)
  })

  it('does not match a different key with the right modifier', () => {
    const event = new KeyboardEvent('keydown', { key: 'a', ctrlKey: true })
    expect(matchesBinding(event, binding, 'other')).toBe(false)
  })

  it('matches a no-modifier binding only when no modifier is held', () => {
    const noModifierBinding: ShortcutBinding = { key: 'j' }
    expect(matchesBinding(new KeyboardEvent('keydown', { key: 'j' }), noModifierBinding, 'other')).toBe(true)
    expect(matchesBinding(new KeyboardEvent('keydown', { key: 'j', ctrlKey: true }), noModifierBinding, 'other')).toBe(false)
  })

  it('is case-insensitive on the key', () => {
    const event = new KeyboardEvent('keydown', { key: 'S', ctrlKey: true })
    expect(matchesBinding(event, binding, 'other')).toBe(true)
  })
})

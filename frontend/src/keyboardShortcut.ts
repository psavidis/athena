/**
 * Two platform/text-entry-sensitive rules ticket #159's Technical
 * Requirements call for, kept independent of any one page: whether a
 * keydown's target is somewhere normal typing must never be intercepted,
 * and whether a keydown matches a binding's modifier the way this host
 * platform expects (Cmd on macOS where every other platform uses Ctrl).
 */

export type Platform = 'mac' | 'other'

export interface ShortcutBinding {
  key: string
  modifier?: 'primary'
}

const TEXT_ENTRY_INPUT_TYPES = new Set(['text', 'search', 'email', 'url', 'tel', 'password', 'number'])

export function isTextEntryTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false
  }
  if (target.isContentEditable || target.contentEditable === 'true') {
    return true
  }
  if (target.tagName === 'TEXTAREA') {
    return true
  }
  if (target.tagName === 'INPUT') {
    const type = (target as HTMLInputElement).type
    return TEXT_ENTRY_INPUT_TYPES.has(type)
  }
  return false
}

export function matchesBinding(event: KeyboardEvent, binding: ShortcutBinding, platform: Platform): boolean {
  if (event.key.toLowerCase() !== binding.key.toLowerCase()) {
    return false
  }
  const primaryHeld = platform === 'mac' ? event.metaKey : event.ctrlKey
  const otherModifierHeld = platform === 'mac' ? event.ctrlKey : event.metaKey
  if (binding.modifier === 'primary') {
    return primaryHeld && !otherModifierHeld
  }
  return !event.ctrlKey && !event.metaKey
}

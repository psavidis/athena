import { fireEvent } from '@testing-library/react'
import { KEY_BINDINGS, type ShortcutAction } from '../keyboardBindings'

export { type ShortcutAction }

/** Fires the real production key binding for `action` as a keydown on `target` (defaults to `document`). */
export function pressShortcut(action: ShortcutAction, target: Document | Element = document) {
  fireEvent.keyDown(target, { key: KEY_BINDINGS[action] })
}

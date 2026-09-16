import { fireEvent } from '@testing-library/react'

/**
 * Placeholder key bindings for ticket #159's keyboard tests. #159 explicitly
 * does not prescribe the final shortcut map ("The exact key bindings should
 * be designed as part of implementation/prototyping") — these keys exist
 * only so a test can name a concrete action; `engineer-ticket` owns picking
 * the real bindings (and must update this map to match, keeping tests and
 * implementation in sync) rather than being bound by these choices.
 */
export const SHORTCUT_KEYS = {
  moveFocusNext: 'Tab',
  leaveRegion: 'Escape',
  nextItem: 'j',
  previousItem: 'k',
  enterItem: 'Enter',
  returnToPreviousContext: 'Escape',
  nextFinding: 'j',
  jumpToRelatedChange: 'g',
  createComment: 'c',
  submitComment: 'Enter',
  cancelComment: 'Escape',
  replyToComment: 'r',
  editComment: 'e',
  resolveComment: 'y',
  jumpToCode: 'g',
  moveToNextSemanticElement: 'j',
  selectElement: 'Enter',
  enterElementContext: 'ArrowRight',
  leaveElementContext: 'ArrowLeft',
  nextSibling: 'j',
  higherLevel: 'ArrowUp',
  lowerLevel: 'ArrowDown',
  zoomIn: '+',
  zoomOut: '-',
  resetView: '0',
  previousZoomLevel: 'Backspace',
  openShortcutReference: '?',
  closeCurrentView: 'Escape',
} as const

export type ShortcutAction = keyof typeof SHORTCUT_KEYS

/** Fires the placeholder key for `action` as a real keydown on `target` (defaults to `document`). */
export function pressShortcut(action: ShortcutAction, target: Document | Element = document) {
  fireEvent.keyDown(target, { key: SHORTCUT_KEYS[action] })
}

/**
 * Ticket #159's placeholder keyboard shortcut map. #159 explicitly does not
 * prescribe the final bindings ("The exact key bindings should be designed
 * as part of implementation/prototyping") — these are a first, reasonable
 * pass (loosely vim/gmail-style: j/k to move, single letters for actions),
 * kept in one place so a future ticket can retune them without hunting
 * through every component. Tests import this same map rather than
 * duplicating the key choices.
 */
export const KEY_BINDINGS = {
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

export type ShortcutAction = keyof typeof KEY_BINDINGS

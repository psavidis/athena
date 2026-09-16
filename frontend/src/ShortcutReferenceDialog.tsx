import { useEffect, useRef } from 'react'

/**
 * A discoverable keyboard-shortcut reference (ticket #159): opened from
 * anywhere with a keyboard shortcut of its own, listing every shortcut
 * group, itself fully keyboard-navigable, and closing back to whatever had
 * focus before it opened.
 */
export interface ShortcutGroup {
  label: string
  shortcuts: { action: string; keys: string }[]
}

export default function ShortcutReferenceDialog({
  isOpen,
  onClose,
  groups,
}: {
  isOpen: boolean
  onClose: () => void
  groups: ShortcutGroup[]
}) {
  const dialogRef = useRef<HTMLDivElement>(null)
  const previouslyFocused = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (isOpen) {
      previouslyFocused.current = document.activeElement as HTMLElement | null
      dialogRef.current?.focus()
    } else {
      previouslyFocused.current?.focus()
    }
  }, [isOpen])

  if (!isOpen) {
    return null
  }

  return (
    <div
      ref={dialogRef}
      role="dialog"
      aria-label="Keyboard shortcuts"
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          onClose()
        }
      }}
      className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 p-6"
    >
      <div className="max-h-[80vh] w-full max-w-lg overflow-auto rounded-2xl border border-canvas-line-strong bg-canvas-paper-raised p-5 shadow-[var(--shadow-canvas-lift)]">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-display text-base font-semibold text-canvas-ink">Keyboard shortcuts</h2>
          <button
            type="button"
            aria-label="Close keyboard shortcuts"
            onClick={onClose}
            className="rounded-md p-1 text-canvas-ink-faint hover:bg-canvas-line hover:text-canvas-ink"
          >
            ✕
          </button>
        </div>
        <div className="flex flex-col gap-4">
          {groups.map((group) => (
            <section key={group.label} role="region" aria-label={group.label} tabIndex={0} className="rounded-lg outline-none focus:ring-2 focus:ring-canvas-gold">
              <h3 className="mb-1.5 text-[11px] font-semibold uppercase tracking-wide text-canvas-ink-faint">{group.label}</h3>
              <ul className="flex flex-col gap-1">
                {group.shortcuts.map((shortcut) => (
                  <li key={shortcut.action} className="flex items-center justify-between text-[13px] text-canvas-ink-soft">
                    <span>{shortcut.action}</span>
                    <kbd className="rounded border border-canvas-line-strong bg-canvas-paper px-1.5 py-0.5 font-mono text-[11px]">
                      {shortcut.keys}
                    </kbd>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      </div>
    </div>
  )
}

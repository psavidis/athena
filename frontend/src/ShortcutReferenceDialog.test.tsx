import { useEffect, useState } from 'react'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import ShortcutReferenceDialog, { type ShortcutGroup } from './ShortcutReferenceDialog'
import { pressShortcut } from './test/keyboardShortcuts'

// Traces frontend/src/test/resources/features/ui_first_experience/keyboard_shortcut_discoverability.feature

const TWO_GROUPS: ShortcutGroup[] = [
  {
    label: 'Navigation',
    shortcuts: [{ action: 'Next item', keys: 'j' }],
  },
  {
    label: 'Comments',
    shortcuts: [{ action: 'Create a comment', keys: 'c' }],
  },
]

function ToggleableDialog() {
  const [open, setOpen] = useState(false)
  return (
    <div>
      <button onClick={() => setOpen(true)}>Open reference</button>
      <ShortcutReferenceDialog isOpen={open} onClose={() => setOpen(false)} groups={TWO_GROUPS} />
    </div>
  )
}

function GlobalShortcutDialog() {
  const [open, setOpen] = useState(false)
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === '?') setOpen(true)
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [])
  return <ShortcutReferenceDialog isOpen={open} onClose={() => setOpen(false)} groups={TWO_GROUPS} />
}

describe('ShortcutReferenceDialog', () => {
  it('is not rendered when closed', () => {
    render(<ShortcutReferenceDialog isOpen={false} onClose={vi.fn()} groups={TWO_GROUPS} />)

    expect(screen.queryByRole('dialog', { name: 'Keyboard shortcuts' })).not.toBeInTheDocument()
  })

  it('opens with keyboard focus inside it, listing every shortcut group', () => {
    render(<ShortcutReferenceDialog isOpen={true} onClose={vi.fn()} groups={TWO_GROUPS} />)

    const dialog = screen.getByRole('dialog', { name: 'Keyboard shortcuts' })
    expect(within(dialog).getByText('Navigation')).toBeVisible()
    expect(within(dialog).getByText('Comments')).toBeVisible()
    expect(dialog.contains(document.activeElement)).toBe(true)
  })

  it('moves keyboard focus between the listed shortcut groups', async () => {
    render(<ShortcutReferenceDialog isOpen={true} onClose={vi.fn()} groups={TWO_GROUPS} />)
    const dialog = screen.getByRole('dialog', { name: 'Keyboard shortcuts' })
    const navigationGroup = within(dialog).getByRole('region', { name: 'Navigation' })
    const commentsGroup = within(dialog).getByRole('region', { name: 'Comments' })
    navigationGroup.focus()

    const user = userEvent.setup()
    await user.tab()

    expect(document.activeElement).toBe(commentsGroup)
  })

  it('closes with the keyboard and returns focus to what was focused before it opened', async () => {
    render(<ToggleableDialog />)
    const trigger = screen.getByRole('button', { name: 'Open reference' })
    const user = userEvent.setup()
    await user.click(trigger)
    const dialog = await screen.findByRole('dialog', { name: 'Keyboard shortcuts' })

    pressShortcut('closeCurrentView', dialog)

    expect(screen.queryByRole('dialog', { name: 'Keyboard shortcuts' })).not.toBeInTheDocument()
    expect(document.activeElement).toBe(trigger)
  })

  it('opens with the keyboard from anywhere via the global shortcut', async () => {
    render(<GlobalShortcutDialog />)

    pressShortcut('openShortcutReference')

    expect(await screen.findByRole('dialog', { name: 'Keyboard shortcuts' })).toBeVisible()
  })
})

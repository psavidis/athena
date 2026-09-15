import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AthenaTopBar, DiffView } from './ui'

const SAMPLE_DIFF = '+ salute()\n- greet()'

function renderTopBarAndDiff() {
  render(
    <>
      <AthenaTopBar />
      <DiffView diff={SAMPLE_DIFF} />
    </>,
  )
}

describe('DiffViewerThemePicker (via AthenaTopBar)', () => {
  it('lists every registered theme, with the current one marked selected', async () => {
    renderTopBarAndDiff()
    await userEvent.click(screen.getByRole('button', { name: 'Diff viewer theme' }))

    const listbox = screen.getByRole('listbox')
    expect(within(listbox).getByRole('option', { name: 'athena' })).toHaveAttribute('aria-selected', 'true')
    expect(within(listbox).getByRole('option', { name: 'athena dark' })).toHaveAttribute('aria-selected', 'false')
    expect(within(listbox).getByRole('option', { name: 'Dracula' })).toHaveAttribute('aria-selected', 'false')
  })

  it('switches a DiffView rendered alongside it to the newly selected theme', async () => {
    renderTopBarAndDiff()
    const addedMarker = screen.getByText('+')
    const beforeColor = addedMarker.style.color

    await userEvent.click(screen.getByRole('button', { name: 'Diff viewer theme' }))
    await userEvent.click(screen.getByRole('option', { name: 'athena dark' }))

    expect(addedMarker.style.color).not.toBe(beforeColor)
  })

  it('closes the theme list after a selection', async () => {
    renderTopBarAndDiff()
    await userEvent.click(screen.getByRole('button', { name: 'Diff viewer theme' }))
    await userEvent.click(screen.getByRole('option', { name: 'athena dark' }))

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })
})

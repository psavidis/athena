import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DiffView } from './ui'

const JAVA_DIFF = [
  '--- a/src/main/java/com/athena/Greeter.java',
  '+++ b/src/main/java/com/athena/Greeter.java',
  '@@ -1,3 +1,3 @@',
  ' public class Greeter {',
  '-  void greet() {}',
  '+  void salute() {}',
  ' }',
].join('\n')

describe('DiffView syntax highlighting', () => {
  it('colors a keyword differently from a class name when the diff header identifies a known language', () => {
    render(<DiffView diff={JAVA_DIFF} />)

    const keyword = screen.getByText('public')
    const className = screen.getByText('Greeter')
    expect(keyword.style.color).not.toBe(className.style.color)
  })

  it('colors the same token type consistently across an unchanged context line and a changed line', () => {
    render(<DiffView diff={JAVA_DIFF} />)

    // "void" appears on both the removed and added line; both should get
    // the same keyword color from the theme regardless of the line's +/- state.
    const [removedVoid, addedVoid] = screen.getAllByText('void')
    expect(removedVoid.style.color).toBe(addedVoid.style.color)
  })

  it('falls back to plain, untyped text for a diff with no recognizable file header', () => {
    render(<DiffView diff={'+ salute()\n- greet()'} />)

    const addedMarker = screen.getByText('+')
    const removedMarker = screen.getByText('-')
    // Markers still carry the inserted/removed colors even without a
    // detected language.
    expect(addedMarker.style.color).not.toBe(removedMarker.style.color)
  })
})

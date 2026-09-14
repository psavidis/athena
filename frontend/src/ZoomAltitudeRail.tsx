import type { SemanticDimension, SemanticDimensionEntry, SemanticProfile } from './api'

/**
 * The six semantic-spine altitude stops (ticket #130), Intent → Code, each
 * naming the {@link SemanticDimension}(s) it draws from and a one-word hint
 * of what it answers. Capability+Flow is one stop over two dimensions (the
 * #125 merge); Code is Structure's evidence endpoint, not an independent
 * stop of its own — reachable only once a Structure node is selected (see
 * SemanticCanvasPage), so it never appears on this rail.
 */
export type AltitudeStop = 'INTENT' | 'CAPABILITY_FLOW' | 'ARCHITECTURE' | 'PATTERN_FRAMEWORK' | 'STRUCTURE'

export const ALTITUDE_STOPS: { stop: AltitudeStop; label: string; hint: string; dimensions: SemanticDimension[] }[] = [
  { stop: 'INTENT', label: 'Intent', hint: 'why', dimensions: ['INTENT'] },
  { stop: 'CAPABILITY_FLOW', label: 'Capability+Flow', hint: 'what + where', dimensions: ['RESPONSIBILITY', 'FEATURE'] },
  { stop: 'ARCHITECTURE', label: 'Architecture', hint: 'how it is shaped', dimensions: ['ARCHITECTURE'] },
  { stop: 'PATTERN_FRAMEWORK', label: 'Pattern+Framework', hint: 'technique', dimensions: ['PATTERN', 'FRAMEWORK'] },
  { stop: 'STRUCTURE', label: 'Structure', hint: 'the atomic edit', dimensions: ['STRUCTURAL'] },
]

/** Entries grouped by which altitude stop they belong to, for compression + "first populated stop" logic. */
export function entriesByStop(profile: SemanticProfile): Map<AltitudeStop, SemanticDimensionEntry[]> {
  const byStop = new Map<AltitudeStop, SemanticDimensionEntry[]>()
  for (const { stop, dimensions } of ALTITUDE_STOPS) {
    const entries = profile.dimensions.filter((entry) => dimensions.includes(entry.dimension))
    if (entries.length > 0) {
      byStop.set(stop, entries)
    }
  }
  return byStop
}

/** Only the stops this PR/territory actually has classified content for, in spine order. */
export function populatedStops(profile: SemanticProfile): AltitudeStop[] {
  const byStop = entriesByStop(profile)
  return ALTITUDE_STOPS.map((s) => s.stop).filter((stop) => byStop.has(stop))
}

/**
 * The zoom-altitude ladder (ticket #128's approved prototype): an inline
 * part of the left sidebar's own flow (not a floating box), each stop a
 * ring connected to its neighbors by a vertical line — a literal "ladder"
 * a reviewer climbs down from Intent toward Structure.
 */
export default function ZoomAltitudeRail({
  profile,
  currentStop,
  onSelectStop,
}: {
  profile: SemanticProfile
  currentStop: AltitudeStop | undefined
  onSelectStop: (stop: AltitudeStop) => void
}) {
  const stops = populatedStops(profile)
  if (stops.length === 0) {
    return null
  }
  const currentIndex = stops.findIndex((s) => s === currentStop)
  return (
    <div className="px-1 pt-1">
      <span className="mb-1.5 block px-1.5 text-[10px] font-semibold uppercase tracking-wide text-canvas-ink-faint">
        Where you are
      </span>
      <nav aria-label="Zoom altitude · semantic spine" className="flex flex-col">
        {stops.map((stop, index) => {
          const meta = ALTITUDE_STOPS.find((s) => s.stop === stop)!
          const isCurrent = stop === currentStop
          const isPassed = currentIndex >= 0 && index < currentIndex
          return (
            <button
              key={stop}
              type="button"
              aria-current={isCurrent ? 'true' : undefined}
              className={`relative flex w-full items-center gap-2 rounded-lg px-1.5 py-1.5 text-left ${
                isCurrent ? 'font-semibold text-canvas-gold-deep' : 'text-canvas-ink-faint hover:bg-canvas-gold-soft'
              }`}
              onClick={() => onSelectStop(stop)}
            >
              {index > 0 && (
                <span className="absolute left-[13px] top-0 h-1/2 w-px bg-canvas-line" aria-hidden="true" />
              )}
              {index < stops.length - 1 && (
                <span className="absolute left-[13px] top-1/2 h-1/2 w-px bg-canvas-line" aria-hidden="true" />
              )}
              <span
                className={`relative z-[1] h-2 w-2 flex-shrink-0 rounded-full border-[1.5px] ${
                  isCurrent
                    ? 'border-canvas-gold bg-canvas-gold shadow-[0_0_0_3px_var(--color-canvas-gold-glow)]'
                    : isPassed
                      ? 'border-canvas-line-strong bg-canvas-line-strong'
                      : 'border-canvas-line-strong bg-canvas-paper-raised'
                }`}
              />
              <span className="flex flex-col leading-tight">
                <span className="text-xs">{meta.label}</span>
                <span className="text-[9.5px] font-normal text-canvas-ink-faint">{meta.hint}</span>
              </span>
            </button>
          )
        })}
      </nav>
    </div>
  )
}

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
  return (
    <nav
      aria-label="Zoom altitude · semantic spine"
      className="absolute left-6 top-6 flex flex-col gap-1 rounded-2xl border border-ink-200 bg-paper-raised p-2 shadow-sm"
    >
      {stops.map((stop) => {
        const meta = ALTITUDE_STOPS.find((s) => s.stop === stop)!
        const isCurrent = stop === currentStop
        return (
          <button
            key={stop}
            type="button"
            aria-current={isCurrent ? 'true' : undefined}
            className={`rounded-xl px-3 py-2 text-left text-sm transition-colors ${
              isCurrent ? 'bg-accent-soft text-accent' : 'text-ink-700 hover:bg-ink-100'
            }`}
            onClick={() => onSelectStop(stop)}
          >
            <span className="block font-medium">{meta.label}</span>
            <span className="block text-xs text-ink-500">{meta.hint}</span>
          </button>
        )
      })}
    </nav>
  )
}

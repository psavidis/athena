import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getSemanticProfile, type SemanticDimension, type SemanticDimensionEntry } from './api'
import ArchitectureLevel from './ArchitectureLevel'
import CapabilityLevel from './CapabilityLevel'
import FlowLevel from './FlowLevel'
import FrameworkLevel from './FrameworkLevel'
import { GUIDED_REVIEW_CHAPTERS } from './guidedReviewChapters'
import IntentLevel from './IntentLevel'
import PatternLevel from './PatternLevel'
import StructureLevel from './StructureLevel'
import { BackLink, Card, DiffView, ErrorState, LoadingState, PrimaryButton, SecondaryButton, SectionLabel } from './ui'

/**
 * The seven-level semantic spine (ticket #91 §2/§12), in Structure → Intent
 * order — the same order used to lay out the spine, walk the Change Story,
 * and default the initially-selected level. `label` is the UI-facing name;
 * `dimension` is the {@link SemanticDimension} it corresponds to on the
 * Change's Semantic Profile (ticket #94).
 */
const LEVELS: { label: string; dimension: SemanticDimension }[] = [
  { label: 'Structure', dimension: 'STRUCTURAL' },
  { label: 'Pattern', dimension: 'PATTERN' },
  { label: 'Framework', dimension: 'FRAMEWORK' },
  { label: 'Capability', dimension: 'RESPONSIBILITY' },
  { label: 'Flow', dimension: 'FEATURE' },
  { label: 'Architecture', dimension: 'ARCHITECTURE' },
  { label: 'Intent', dimension: 'INTENT' },
]

export default function SemanticChangeExplorerPage({
  changeKey,
  onBack,
}: {
  changeKey: string
  onBack: () => void
}) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['semantic-profile', changeKey],
    queryFn: () => getSemanticProfile(changeKey),
    retry: false,
  })
  const [currentDimension, setCurrentDimension] = useState<SemanticDimension>('STRUCTURAL')
  // Which entry of the current level is highlighted: a Structure chip the
  // reviewer clicked directly, or the Structure chip a Pattern's "supported
  // by" link sent them to (ticket #96); or a Capability card the reviewer
  // clicked, to show its evidence (ticket #97). Cleared on any other level
  // change — it's only meaningful while looking at Structure or Capability.
  const [selectedConceptName, setSelectedConceptName] = useState<string | undefined>(undefined)
  // The globally-selected concept driving cross-highlighting (ticket #99
  // §10): any classified concept, on any level, that the reviewer clicked
  // — a Change Story bracketed term, or (in a level's own selection
  // mechanism) whichever concept it reports as selected. Independent of
  // selectedConceptName/currentDimension: it survives a level change so
  // clicking a Change Story term keeps its cross-highlight active even
  // after switching levels.
  const [selectedGlobalEntry, setSelectedGlobalEntry] = useState<SemanticDimensionEntry | undefined>(undefined)
  // Guided Review (ticket #100 / #91 §14): undefined means the reviewer is
  // in the normal spine-driven Explorer; a chapter index means they're
  // walking GUIDED_REVIEW_CHAPTERS instead. Kept separate from
  // currentDimension so exiting can restore exactly the level the reviewer
  // was on before entering, without Guided Review's own navigation
  // disturbing it.
  const [guidedReviewChapterIndex, setGuidedReviewChapterIndex] = useState<number | undefined>(undefined)
  // The concept name the reviewer is currently hovering, if any (ticket
  // #101: "hovering a semantic element subtly pulses its supporting
  // evidence"). Purely transient UI feedback — unrelated to selection, so
  // it never affects selectedConceptName/selectedGlobalEntry and clears on
  // mouse-out rather than needing an explicit "unselect" action.
  const [hoveredConceptName, setHoveredConceptName] = useState<string | undefined>(undefined)

  if (isError) {
    return <ErrorState message="Could not load this Change's Semantic Profile." />
  }
  if (isLoading || !data) {
    return <LoadingState />
  }

  const dimensions = data.dimensions

  function selectLevel(dimension: SemanticDimension) {
    setCurrentDimension(dimension)
    setSelectedConceptName(undefined)
  }

  function selectSupportingStructuralChange(conceptName: string) {
    setCurrentDimension('STRUCTURAL')
    setSelectedConceptName(conceptName)
  }

  function selectGlobalConcept(entry: SemanticDimensionEntry) {
    setCurrentDimension(entry.dimension)
    setSelectedConceptName(undefined)
    setSelectedGlobalEntry(entry)
  }

  function entriesForDimension(dimension: SemanticDimension) {
    return dimensions.filter((entry) => entry.dimension === dimension)
  }

  function renderLevel(dimension: SemanticDimension) {
    const entries = entriesForDimension(dimension)
    const label = LEVELS.find((level) => level.dimension === dimension)!.label
    switch (dimension) {
      case 'STRUCTURAL':
        return (
          <StructureLevel entries={entries} selectedConceptName={selectedConceptName} onSelect={setSelectedConceptName} />
        )
      case 'PATTERN':
        return (
          <PatternLevel
            entries={entries}
            onSelectSupporting={selectSupportingStructuralChange}
            onSelectConcept={selectGlobalConcept}
            onHoverConcept={setHoveredConceptName}
          />
        )
      case 'FRAMEWORK':
        return <FrameworkLevel entries={entries} />
      case 'RESPONSIBILITY':
        return (
          <CapabilityLevel
            entries={entries}
            selectedConceptName={selectedConceptName}
            onSelect={(conceptName) => {
              setSelectedConceptName(conceptName)
              const entry = entries.find((e) => e.conceptName === conceptName)
              if (entry) {
                setSelectedGlobalEntry(entry)
              }
            }}
          />
        )
      case 'FEATURE':
        return <FlowLevel entries={entries} />
      case 'ARCHITECTURE':
        return <ArchitectureLevel entries={entries} onSelectConcept={selectGlobalConcept} />
      case 'INTENT':
        return <IntentLevel entries={entries} />
      default:
        return <CenterStage label={label} entry={entries[0]} />
    }
  }

  if (guidedReviewChapterIndex !== undefined) {
    return (
      <GuidedReviewChapterView
        chapterIndex={guidedReviewChapterIndex}
        onBack={() => setGuidedReviewChapterIndex((index) => index! - 1)}
        onContinue={() => setGuidedReviewChapterIndex((index) => index! + 1)}
        onExit={() => setGuidedReviewChapterIndex(undefined)}
        renderLevel={renderLevel}
        allDimensions={dimensions}
      />
    )
  }

  const entriesForCurrentDimension = entriesForDimension(currentDimension)
  const currentLabel = LEVELS.find((level) => level.dimension === currentDimension)!.label
  const highlightedEntries = entriesSharingEvidence(dimensions, selectedGlobalEntry)
  // The evidence panel shows the current level's own entries; while a
  // cross-highlight is active (ticket #99 §10), it shows every classified
  // entry across every dimension instead — connected ones highlighted,
  // everything else visually subdued rather than removed, per #91 §10's
  // closing rule ("unrelated content is visually subdued, not removed").
  const entriesForEvidencePanel = highlightedEntries.size > 0 ? dimensions : entriesForCurrentDimension

  return (
    <div className="mx-auto max-w-6xl px-6 py-10 sm:px-10 sm:py-14">
      <div className="animate-rise-in">
        <div className="mb-2 flex items-center justify-between">
          <BackLink onClick={onBack}>← Back to Change Map</BackLink>
          <SecondaryButton onClick={() => setGuidedReviewChapterIndex(0)}>Start guided review</SecondaryButton>
        </div>

        <ChangeStory dimensions={dimensions} onSelect={selectLevel} onSelectConcept={selectGlobalConcept} />

        <div className="grid gap-6 lg:grid-cols-[14rem_1fr_1fr]">
          <Spine current={currentDimension} onSelect={selectLevel} />
          {/* Keyed on currentDimension so switching levels replays the rise-in
              animation (ticket #101: "moving between semantic levels
              transitions the same change into its new representation ...
              rather than reading as a new page load"), instead of a silent,
              instant swap — while keeping the spine/evidence panel around it
              stable, so the reviewer's place in the Explorer isn't disturbed. */}
          <div key={currentDimension} className="animate-rise-in">
            {renderLevel(currentDimension)}
          </div>
          <EvidencePanel
            label={currentLabel}
            entries={entriesForEvidencePanel}
            selectedConceptName={selectedConceptName}
            highlightedEntries={highlightedEntries}
            hoveredConceptName={hoveredConceptName}
          />
        </div>
      </div>
    </div>
  )
}

/**
 * The Guided Review chapter view (ticket #100 / #91 §14): walks
 * GUIDED_REVIEW_CHAPTERS in order, rendering each chapter's dimension(s)
 * side by side via the same per-level components the spine-driven Explorer
 * uses. The final "Inspect the evidence" chapter has no dimensions of its
 * own — it shows every classified entry's evidence instead, since it is
 * the change's raw evidence rather than another semantic level.
 */
function GuidedReviewChapterView({
  chapterIndex,
  onBack,
  onContinue,
  onExit,
  renderLevel,
  allDimensions,
}: {
  chapterIndex: number
  onBack: () => void
  onContinue: () => void
  onExit: () => void
  renderLevel: (dimension: SemanticDimension) => React.ReactNode
  allDimensions: SemanticDimensionEntry[]
}) {
  const chapter = GUIDED_REVIEW_CHAPTERS[chapterIndex]
  const isFirst = chapterIndex === 0
  const isLast = chapterIndex === GUIDED_REVIEW_CHAPTERS.length - 1

  return (
    <div className="mx-auto max-w-6xl px-6 py-10 sm:px-10 sm:py-14">
      <div className="animate-rise-in">
        <div className="mb-2 flex items-center justify-between">
          <SectionLabel>Chapter {chapterIndex + 1} of {GUIDED_REVIEW_CHAPTERS.length}</SectionLabel>
          <SecondaryButton onClick={onExit}>Exit guided review</SecondaryButton>
        </div>

        <h1 className="mb-6 text-2xl font-semibold tracking-tight text-ink-900">{chapter.title}</h1>

        <nav aria-label="Guided review chapters" className="mb-8">
          <ol className="flex flex-wrap gap-2">
            {GUIDED_REVIEW_CHAPTERS.map((c, i) => (
              <li
                key={c.title}
                aria-current={i === chapterIndex ? 'true' : undefined}
                className={
                  'rounded-full px-3 py-1 text-xs font-medium ' +
                  (i === chapterIndex ? 'bg-ink-900 text-white' : i < chapterIndex ? 'bg-ink-200 text-ink-700' : 'bg-ink-100 text-ink-500')
                }
              >
                {c.title}
              </li>
            ))}
          </ol>
        </nav>

        {chapter.dimensions.length > 0 ? (
          <div className={'grid gap-6 ' + (chapter.dimensions.length > 1 ? 'lg:grid-cols-2' : 'lg:max-w-3xl')}>
            {chapter.dimensions.map((dimension) => (
              <div key={dimension}>{renderLevel(dimension)}</div>
            ))}
          </div>
        ) : (
          <EvidencePanel
            label="the Change"
            entries={allDimensions}
            selectedConceptName={undefined}
            highlightedEntries={new Set()}
          />
        )}

        <div className="mt-8 flex justify-between">
          {!isFirst ? <SecondaryButton onClick={onBack}>Back</SecondaryButton> : <span />}
          {!isLast && <PrimaryButton onClick={onContinue}>Continue</PrimaryButton>}
        </div>
      </div>
    </div>
  )
}

/**
 * Every classification (across all seven dimensions) that shares at least
 * one piece of evidence with {@code selected} — the real, derivable signal
 * cross-highlighting is built on (ticket #99 §10's "driven by the existing
 * evidence/matched-occurrence data ... not a new relationship model"):
 * every classifier passes a Change's own diff evidence verbatim, so two
 * classifications on different dimensions of the same Change genuinely
 * share evidence text when they're both derived from it. Always includes
 * {@code selected} itself. Empty (no highlighting active) when nothing is
 * selected.
 */
function entriesSharingEvidence(
  allEntries: SemanticDimensionEntry[],
  selected: SemanticDimensionEntry | undefined,
): Set<SemanticDimensionEntry> {
  if (!selected) {
    return new Set()
  }
  const selectedEvidence = new Set(selected.evidence)
  return new Set(
    allEntries.filter((entry) => entry === selected || entry.evidence.some((diff) => selectedEvidence.has(diff))),
  )
}

function Spine({
  current,
  onSelect,
}: {
  current: SemanticDimension
  onSelect: (dimension: SemanticDimension) => void
}) {
  return (
    <nav aria-label="Semantic levels">
      <SectionLabel>Semantic Spine</SectionLabel>
      <ul className="space-y-1">
        {LEVELS.map((level) => (
          <li key={level.dimension}>
            <button
              type="button"
              aria-current={level.dimension === current ? 'true' : undefined}
              onClick={() => onSelect(level.dimension)}
              className={
                'w-full rounded-lg px-3 py-2 text-left text-sm font-medium transition-colors ' +
                (level.dimension === current ? 'bg-ink-900 text-white' : 'text-ink-700 hover:bg-ink-100')
              }
            >
              {level.label}
            </button>
          </li>
        ))}
      </ul>
    </nav>
  )
}

function CenterStage({ label, entry }: { label: string; entry: SemanticDimensionEntry | undefined }) {
  return (
    <section>
      <SectionLabel>{label}</SectionLabel>
      <Card className="p-5">
        {entry ? (
          <>
            <div className="mb-2 flex items-center gap-2">
              <h2 className="text-lg font-semibold text-ink-900">{entry.conceptName}</h2>
              <ConfidenceChip entry={entry} />
            </div>
            <p className="text-sm text-ink-700">{entry.conceptDescription}</p>
          </>
        ) : (
          <p className="text-sm text-ink-500">No {label} classification for this Change yet.</p>
        )}
      </Card>
    </section>
  )
}

function ConfidenceChip({ entry }: { entry: SemanticDimensionEntry }) {
  return (
    <span
      className={
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ' +
        (entry.inferred ? 'bg-amber-50 text-amber-800' : 'bg-emerald-50 text-emerald-800')
      }
    >
      {entry.inferred ? 'Inferred' : 'Observed'} · {entry.confidencePercent}%
    </span>
  )
}

function EvidencePanel({
  label,
  entries,
  selectedConceptName,
  highlightedEntries,
  hoveredConceptName,
}: {
  label: string
  entries: SemanticDimensionEntry[]
  selectedConceptName: string | undefined
  highlightedEntries: Set<SemanticDimensionEntry>
  hoveredConceptName?: string
}) {
  const withEvidence = entries.filter((entry) => entry.evidence.length > 0)
  const crossHighlighting = highlightedEntries.size > 0
  return (
    <section>
      <SectionLabel>Evidence</SectionLabel>
      {withEvidence.length > 0 ? (
        <div className="space-y-3">
          {withEvidence.map((entry) => {
            const isSelected = entry.conceptName === selectedConceptName
            const isCrossHighlighted = highlightedEntries.has(entry)
            const isSubdued = crossHighlighting && !isCrossHighlighted
            const isHoverEmphasized = entry.conceptName === hoveredConceptName
            return (
              <div
                key={entry.dimension + ':' + entry.conceptName}
                role="group"
                aria-label={entry.conceptName}
                aria-current={isSelected || isCrossHighlighted ? 'true' : undefined}
                data-hover-emphasized={isHoverEmphasized ? 'true' : undefined}
                className={
                  'space-y-3 rounded-xl transition-[opacity,box-shadow] duration-200 ' +
                  (isSelected || isCrossHighlighted ? 'ring-2 ring-accent ring-offset-2' : '') +
                  (isSubdued ? ' opacity-40' : '') +
                  (isHoverEmphasized ? ' ring-2 ring-accent/60' : '')
                }
              >
                {entry.evidence.map((diff, i) => (
                  <DiffView key={i} diff={diff} />
                ))}
              </div>
            )
          })}
        </div>
      ) : (
        <p className="text-sm text-ink-500">No evidence available for the {label} level.</p>
      )}
    </section>
  )
}

/**
 * The interactive sentence bridging the diff to its meaning (ticket #91
 * §11). This ticket's scope is the click-to-select mechanic and reflecting
 * the Change's real classifications — not the fully-templated narrative
 * prose from #91's own example (which needs data this Semantic Profile
 * doesn't carry, like the enclosing type name), so classified concepts are
 * joined plainly, in spine order. Clicking a bracketed term also drives
 * cross-highlighting (ticket #99 §10): {@code onSelectConcept} carries the
 * actual entry, not just its dimension, so the Explorer can highlight every
 * other classification sharing its evidence.
 */
function ChangeStory({
  dimensions,
  onSelect,
  onSelectConcept,
}: {
  dimensions: SemanticDimensionEntry[]
  onSelect: (dimension: SemanticDimension) => void
  onSelectConcept: (entry: SemanticDimensionEntry) => void
}) {
  const byDimension = new Map(dimensions.map((entry) => [entry.dimension, entry]))
  // Structure is the raw evidence layer ("the diff is the evidence"), not part of the
  // narrated story — #91 §11's own example sentence starts at Pattern, never Structure.
  const ordered = LEVELS.filter((level) => level.dimension !== 'STRUCTURAL')
    .map((level) => byDimension.get(level.dimension))
    .filter((entry): entry is SemanticDimensionEntry => entry !== undefined)

  if (ordered.length === 0) {
    return null
  }

  return (
    <p className="mb-8 font-serif text-2xl leading-relaxed text-ink-900">
      {ordered.map((entry, i) => (
        <span key={entry.dimension}>
          {i > 0 && <span className="text-ink-300"> → </span>}
          <button
            type="button"
            onClick={() => {
              onSelect(entry.dimension)
              onSelectConcept(entry)
            }}
            className="rounded px-0.5 underline decoration-accent/40 decoration-2 underline-offset-4 transition-colors hover:bg-accent-soft hover:text-accent"
          >
            {entry.conceptName}
          </button>
        </span>
      ))}
    </p>
  )
}

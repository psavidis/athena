// Shared visual primitives implementing docs/specs/visual-design-philosophy.md:
// a calm, precise visual language reused across every page so review states,
// hierarchy, and motion read consistently no matter where the reviewer is.
import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listOpenPullRequests, listRepositories } from './api'
import type { ChangeCategory, PullRequestSummary, ReviewState, SemanticDimension } from './api'
import { pullRequestPath } from './shareUrl'

// One hue + glyph per Change category (index.css defines the underlying
// colors), reused for section headers, count chips, and card accents so a
// category is recognizable by shape/color, not just by reading its label.
export const CATEGORY_META: Record<
  ChangeCategory,
  {
    label: string
    text: string
    soft: string
    border: string
    ring: string
    solid: string
    gradient: string
    icon: React.ReactNode
  }
> = {
  BEHAVIORAL: {
    label: 'Behavioral',
    text: 'text-behavioral',
    soft: 'bg-behavioral-soft',
    border: 'border-behavioral',
    ring: 'ring-behavioral/20',
    solid: 'bg-behavioral',
    gradient: 'from-behavioral to-violet-400',
    icon: (
      <svg viewBox="0 0 20 20" fill="none" className="h-full w-full">
        <path d="M10 2.5 3 6v5c0 4 3 6.9 7 8.5 4-1.6 7-4.5 7-8.5V6l-7-3.5Z" fill="currentColor" fillOpacity="0.15" />
        <path
          d="M10 2.5 3 6v5c0 4 3 6.9 7 8.5 4-1.6 7-4.5 7-8.5V6l-7-3.5Z"
          stroke="currentColor"
          strokeWidth="1.4"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
  STRUCTURAL: {
    label: 'Structural',
    text: 'text-structural',
    soft: 'bg-structural-soft',
    border: 'border-structural',
    ring: 'ring-structural/20',
    solid: 'bg-structural',
    gradient: 'from-structural to-sky-400',
    icon: (
      <svg viewBox="0 0 20 20" fill="none" className="h-full w-full">
        <rect x="2.5" y="3" width="6" height="6" rx="1.3" fill="currentColor" fillOpacity="0.15" stroke="currentColor" strokeWidth="1.4" />
        <rect x="11.5" y="11" width="6" height="6" rx="1.3" fill="currentColor" fillOpacity="0.15" stroke="currentColor" strokeWidth="1.4" />
        <path d="M8.5 6h3a2 2 0 0 1 2 2v3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      </svg>
    ),
  },
  MECHANICAL: {
    label: 'Mechanical',
    text: 'text-mechanical',
    soft: 'bg-mechanical-soft',
    border: 'border-mechanical',
    ring: 'ring-mechanical/20',
    solid: 'bg-mechanical',
    gradient: 'from-mechanical to-stone-400',
    icon: (
      <svg viewBox="0 0 20 20" fill="none" className="h-full w-full">
        <path
          d="M15.5 11.4a3.6 3.6 0 0 0 .1-1.4l1.4-1.1-1.3-2.3-1.7.5a3.6 3.6 0 0 0-1.2-.7l-.3-1.7H9.9l-.3 1.7a3.6 3.6 0 0 0-1.2.7l-1.7-.5-1.3 2.3 1.4 1.1a3.6 3.6 0 0 0 0 1.4l-1.4 1.1 1.3 2.3 1.7-.5c.36.3.77.53 1.2.7l.3 1.7h2.6l.3-1.7c.43-.17.84-.4 1.2-.7l1.7.5 1.3-2.3-1.4-1.1Z"
          fill="currentColor"
          fillOpacity="0.15"
          stroke="currentColor"
          strokeWidth="1.3"
          strokeLinejoin="round"
        />
        <circle cx="11.2" cy="10" r="2" fill="currentColor" fillOpacity="0.25" stroke="currentColor" strokeWidth="1.3" />
      </svg>
    ),
  },
  UNKNOWN: {
    label: 'Unknown',
    text: 'text-unknown',
    soft: 'bg-unknown-soft',
    border: 'border-unknown',
    ring: 'ring-unknown/20',
    solid: 'bg-unknown',
    gradient: 'from-unknown to-amber-400',
    icon: (
      <svg viewBox="0 0 20 20" fill="none" className="h-full w-full">
        <circle cx="10" cy="10" r="7.2" fill="currentColor" fillOpacity="0.15" stroke="currentColor" strokeWidth="1.4" />
        <path
          d="M7.8 8a2.2 2.2 0 1 1 3.3 1.9c-.7.4-1.1.8-1.1 1.6"
          stroke="currentColor"
          strokeWidth="1.4"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle cx="10" cy="14" r="0.9" fill="currentColor" />
      </svg>
    ),
  },
}

// One hue per Semantic Change Explorer spine level (index.css defines the
// underlying --color-lv-* tokens), reused for the spine nav dot, confidence
// chips, and level headers so a level is recognizable by color the same way
// a CATEGORY_META entry makes a Change category recognizable — cool
// (Structure) to warm (Intent), evidence to meaning (ticket #91 §2, #122).
export const LEVEL_META: Record<
  SemanticDimension,
  { text: string; dot: string; soft: string; hoverSoft: string; ring: string }
> = {
  STRUCTURAL: {
    text: 'text-lv-structure',
    dot: 'bg-lv-structure',
    soft: 'bg-lv-structure-soft',
    hoverSoft: 'hover:bg-lv-structure-soft',
    ring: 'ring-lv-structure/20',
  },
  PATTERN: {
    text: 'text-lv-pattern',
    dot: 'bg-lv-pattern',
    soft: 'bg-lv-pattern-soft',
    hoverSoft: 'hover:bg-lv-pattern-soft',
    ring: 'ring-lv-pattern/20',
  },
  FRAMEWORK: {
    text: 'text-lv-framework',
    dot: 'bg-lv-framework',
    soft: 'bg-lv-framework-soft',
    hoverSoft: 'hover:bg-lv-framework-soft',
    ring: 'ring-lv-framework/20',
  },
  RESPONSIBILITY: {
    text: 'text-lv-capability',
    dot: 'bg-lv-capability',
    soft: 'bg-lv-capability-soft',
    hoverSoft: 'hover:bg-lv-capability-soft',
    ring: 'ring-lv-capability/20',
  },
  FEATURE: {
    text: 'text-lv-flow',
    dot: 'bg-lv-flow',
    soft: 'bg-lv-flow-soft',
    hoverSoft: 'hover:bg-lv-flow-soft',
    ring: 'ring-lv-flow/20',
  },
  ARCHITECTURE: {
    text: 'text-lv-architecture',
    dot: 'bg-lv-architecture',
    soft: 'bg-lv-architecture-soft',
    hoverSoft: 'hover:bg-lv-architecture-soft',
    ring: 'ring-lv-architecture/20',
  },
  INTENT: {
    text: 'text-lv-intent',
    dot: 'bg-lv-intent',
    soft: 'bg-lv-intent-soft',
    hoverSoft: 'hover:bg-lv-intent-soft',
    ring: 'ring-lv-intent/20',
  },
}

/** A level's confidence chip (Observed, or Inferred · NN%), colored by its spine level
 * rather than a fixed emerald/amber pair, so the color itself carries which level this is. */
export function LevelConfidenceChip({
  dimension,
  inferred,
  confidencePercent,
}: {
  dimension: SemanticDimension
  inferred: boolean
  confidencePercent: number
}) {
  const meta = LEVEL_META[dimension]
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ${meta.soft} ${meta.text}`}>
      {inferred && (
        <span className="h-1 w-6 overflow-hidden rounded-full bg-black/10">
          <span className="block h-full rounded-full bg-current" style={{ width: `${confidencePercent}%` }} />
        </span>
      )}
      {inferred ? 'Inferred' : 'Observed'} · {confidencePercent}%
    </span>
  )
}

export function CategoryIcon({ category, className = 'h-4 w-4' }: { category: ChangeCategory; className?: string }) {
  const meta = CATEGORY_META[category]
  return (
    <span className={`${className} ${meta.text}`} aria-hidden="true">
      {meta.icon}
    </span>
  )
}

/** A solid gradient badge with a white glyph — the visual anchor of a category section header. */
export function CategoryBadge({ category, size = 'md' }: { category: ChangeCategory; size?: 'sm' | 'md' | 'lg' }) {
  const meta = CATEGORY_META[category]
  const dims = size === 'lg' ? 'h-11 w-11 p-2.5' : size === 'md' ? 'h-9 w-9 p-2' : 'h-7 w-7 p-1.5'
  return (
    <span
      className={`flex shrink-0 items-center justify-center rounded-xl bg-gradient-to-br text-white shadow-sm ${meta.gradient} ${dims}`}
      aria-hidden="true"
    >
      <span className="h-full w-full [&_*]:!stroke-white [&_*]:!fill-white/25">{meta.icon}</span>
    </span>
  )
}

export function CountChip({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold tabular-nums ${className}`}>
      {children}
    </span>
  )
}

export const REVIEW_STATE_META: Record<
  ReviewState,
  { label: string; dot: string; text: string; bg: string; ring: string }
> = {
  UNSEEN: { label: 'Unseen', dot: 'bg-state-unseen', text: 'text-ink-500', bg: 'bg-ink-100', ring: 'ring-ink-200' },
  UNDERSTANDING: {
    label: 'Understanding',
    dot: 'bg-state-understanding',
    text: 'text-amber-800',
    bg: 'bg-amber-50',
    ring: 'ring-amber-200',
  },
  REVIEWED: {
    label: 'Reviewed',
    dot: 'bg-state-reviewed',
    text: 'text-emerald-800',
    bg: 'bg-emerald-50',
    ring: 'ring-emerald-200',
  },
  CONCERN: { label: 'Concern', dot: 'bg-state-concern', text: 'text-red-800', bg: 'bg-red-50', ring: 'ring-red-200' },
  SKIPPED: { label: 'Skipped', dot: 'bg-state-skipped', text: 'text-ink-500', bg: 'bg-ink-100', ring: 'ring-ink-200' },
}

export function StateDot({ state }: { state: ReviewState }) {
  const meta = REVIEW_STATE_META[state]
  return <span className={`inline-block h-2 w-2 shrink-0 rounded-full ${meta.dot}`} aria-hidden="true" />
}

export function StateBadge({ state }: { state: ReviewState }) {
  const meta = REVIEW_STATE_META[state]
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset ${meta.bg} ${meta.text} ${meta.ring}`}
    >
      <StateDot state={state} />
      {meta.label}
    </span>
  )
}

/**
 * The one top bar shown on every screen (ticket #128's approved prototype:
 * a persistent brand bar, present from the very first repo-picker screen
 * through the Semantic Canvas — never just on the canvas). Before this, the
 * pre-PR screens (`App.tsx`'s `AppHeader`) used a smaller, unstyled logo
 * with none of the prototype's gold-ring/Fraunces treatment, so it read as
 * a different, lesser bar rather than the same persistent one — this is the
 * single source of that styling, composed by both the pre-PR header and the
 * canvas's own top bar (`CanvasTopBar` in SemanticCanvasPage.tsx) so they
 * can never drift apart again. `right` holds page-specific controls (the
 * canvas's review-mode select; nothing, on the pre-PR screens).
 */
/**
 * The emblem + "ATHENA" wordmark, reacting together to the pointer so the
 * brand reads as watching the viewer rather than a static image: the
 * medallion tilts in 3D toward the cursor (a real head-turn, since the
 * artwork is one flat image with no separate eye layer to move), and the
 * gold wordmark brightens/shimmers in step so both pieces feel like one
 * living reaction instead of the image alone doing something.
 */
function WatchingEmblem({ prChip }: { prChip?: React.ReactNode }) {
  const wrapRef = useRef<HTMLDivElement>(null)
  const [tilt, setTilt] = useState({ rx: 0, ry: 0, glowX: 50, glowY: 50 })
  const [active, setActive] = useState(false)

  const handleMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const el = wrapRef.current
    if (!el) return
    const rect = el.getBoundingClientRect()
    const px = (e.clientX - rect.left) / rect.width // 0..1
    const py = (e.clientY - rect.top) / rect.height // 0..1
    const maxDeg = 14
    setTilt({
      rx: (0.5 - py) * maxDeg,
      ry: (px - 0.5) * maxDeg,
      glowX: px * 100,
      glowY: py * 100,
    })
  }

  return (
    <div className="flex min-w-0 items-center gap-3">
      <div
        ref={wrapRef}
        onMouseEnter={() => setActive(true)}
        onMouseMove={handleMove}
        onMouseLeave={() => {
          setActive(false)
          setTilt({ rx: 0, ry: 0, glowX: 50, glowY: 50 })
        }}
        style={{ perspective: '600px' }}
        className="flex-shrink-0"
      >
        <img
          src="/athena-logo.png"
          alt="Athena"
          className="h-11 w-11 rounded-full transition-transform duration-200 ease-out"
          style={{
            transform: `scale(${active ? 1.08 : 1}) rotateX(${tilt.rx}deg) rotateY(${tilt.ry}deg)`,
            boxShadow: active
              ? `0 0 32px 10px var(--color-canvas-gold-glow), 0 0 0 2px var(--color-canvas-gold)`
              : '0 0 0 0 transparent',
            transitionProperty: 'transform, box-shadow',
          }}
        />
      </div>
      <span
        className="whitespace-nowrap font-display text-lg font-bold tracking-[0.12em] bg-clip-text text-transparent transition-all duration-200 ease-out"
        style={{
          backgroundImage: active
            ? 'linear-gradient(180deg, #fff3d0 0%, #f3dfa0 25%, #cf9f3f 55%, #8a6420 85%, #b6862e 100%)'
            : 'linear-gradient(180deg, #f3dfa0 0%, #cf9f3f 35%, #8a6420 70%, #b6862e 100%)',
          textShadow: active ? '0 0 18px rgba(169, 129, 47, 0.45)' : '0 0 0 transparent',
          transform: active ? 'translateX(2px)' : 'translateX(0)',
        }}
      >
        ATHENA
      </span>
      {prChip}
    </div>
  )
}

export function AthenaTopBar({
  prChip,
  picker,
  right,
}: {
  prChip?: React.ReactNode
  picker?: React.ReactNode
  right?: React.ReactNode
}) {
  return (
    <header className="flex flex-wrap items-center justify-between gap-3.5 border-b border-canvas-line bg-canvas-paper-raised px-4 py-2">
      <div className="flex min-w-0 flex-1 items-center gap-4">
        <WatchingEmblem prChip={prChip} />
        {picker}
      </div>
      {right && <div className="flex items-center gap-2.5">{right}</div>}
    </header>
  )
}

/**
 * A single dropdown trigger + popover list — the shared shape behind both
 * steps of {@link RepoPrPicker} (repository, then PR), so the two chained
 * dropdowns look and behave identically rather than one being bespoke.
 * Closes on outside click/Escape; the trigger shows `label` and opens
 * `options` below it on click.
 */
function TopBarDropdown({
  label,
  placeholder,
  disabled,
  loading,
  error,
  options,
  onSelect,
  minWidth = 'min-w-[11rem]',
}: {
  label: string
  placeholder: string
  disabled?: boolean
  loading?: boolean
  error?: boolean
  options: { key: string; label: string }[]
  onSelect: (key: string) => void
  minWidth?: string
}) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function onOutside(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    function onEscape(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onOutside)
    document.addEventListener('keydown', onEscape)
    return () => {
      document.removeEventListener('mousedown', onOutside)
      document.removeEventListener('keydown', onEscape)
    }
  }, [open])

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((current) => !current)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={`flex items-center gap-1.5 rounded-lg border border-canvas-line-strong bg-canvas-paper px-2.5 py-1.5 text-xs text-canvas-ink transition-colors hover:border-canvas-gold disabled:cursor-not-allowed disabled:opacity-50 ${minWidth}`}
      >
        <span className={`truncate ${label ? '' : 'text-canvas-ink-faint'}`}>{label || placeholder}</span>
        <svg viewBox="0 0 12 8" className="ml-auto h-2.5 w-2.5 shrink-0 text-canvas-ink-faint" fill="none" aria-hidden="true">
          <path d="M1 1.5 6 6.5 11 1.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      {open && (
        <div
          role="listbox"
          className="absolute left-0 top-[calc(100%+4px)] z-20 max-h-72 w-64 overflow-y-auto rounded-lg border border-canvas-line-strong bg-canvas-paper-raised py-1 shadow-lg"
        >
          {loading && <p className="px-3 py-2 text-xs text-canvas-ink-faint">Loading…</p>}
          {error && <p className="px-3 py-2 text-xs text-red-600">Could not load.</p>}
          {!loading && !error && options.length === 0 && (
            <p className="px-3 py-2 text-xs text-canvas-ink-faint">None found.</p>
          )}
          {!loading &&
            !error &&
            options.map((option) => (
              <button
                key={option.key}
                type="button"
                role="option"
                onClick={() => {
                  onSelect(option.key)
                  setOpen(false)
                }}
                className="block w-full truncate px-3 py-1.5 text-left text-xs text-canvas-ink hover:bg-canvas-gold-soft"
              >
                {option.label}
              </button>
            ))}
        </div>
      )}
    </div>
  )
}

/**
 * The repository/PR selector, integrated into {@link AthenaTopBar} (rather
 * than the standalone "Select a repository" / "Open PRs" screens App.tsx
 * used to show before landing on the Semantic Canvas) so switching what
 * you're reviewing never leaves the main product surface. Two chained
 * dropdowns: choosing a repository loads and enables its PR dropdown.
 */
export function RepoPrPicker({
  selectedRepo,
  selectedPr,
  onSelectRepo,
  onSelectPr,
}: {
  selectedRepo: string | null
  selectedPr: PullRequestSummary | null
  onSelectRepo: (repositoryFullName: string) => void
  onSelectPr: (pr: PullRequestSummary) => void
}) {
  const reposQuery = useQuery({ queryKey: ['repositories'], queryFn: listRepositories })
  const pullsQuery = useQuery({
    queryKey: ['pulls', selectedRepo],
    queryFn: () => listOpenPullRequests(selectedRepo!),
    enabled: selectedRepo !== null,
  })

  return (
    <div className="flex min-w-0 items-center gap-2">
      <TopBarDropdown
        label={selectedRepo ?? ''}
        placeholder="Select a repository…"
        loading={reposQuery.isLoading}
        error={reposQuery.isError}
        options={(reposQuery.data ?? []).map((repo) => ({ key: repo.fullName, label: repo.fullName }))}
        onSelect={onSelectRepo}
        minWidth="min-w-[13rem]"
      />
      <span className="text-canvas-ink-faint">/</span>
      <TopBarDropdown
        label={selectedPr ? `#${selectedPr.number} ${selectedPr.title}` : ''}
        placeholder="Select a PR…"
        disabled={!selectedRepo}
        loading={pullsQuery.isLoading}
        error={pullsQuery.isError}
        options={(pullsQuery.data ?? []).map((pr) => ({ key: String(pr.number), label: `#${pr.number} ${pr.title}` }))}
        onSelect={(key) => {
          const pr = pullsQuery.data?.find((p) => String(p.number) === key)
          if (pr) onSelectPr(pr)
        }}
        minWidth="min-w-[16rem]"
      />
      {selectedRepo && selectedPr && (
        <ShareLinkButton repositoryFullName={selectedRepo} number={selectedPr.number} />
      )}
    </div>
  )
}

/**
 * Copies the current PR's shareable URL (`/repositories/:owner/:repo/pulls/:number`,
 * see shareUrl.ts) to the clipboard — sending it to a teammate running Athena
 * locally re-selects the same PR against their own GitHub session. Briefly
 * confirms the copy inline rather than via a toast/alert.
 */
function ShareLinkButton({ repositoryFullName, number }: { repositoryFullName: string; number: number }) {
  const [copied, setCopied] = useState(false)

  async function copyLink() {
    const url = `${window.location.origin}${pullRequestPath({ repositoryFullName, number })}`
    await navigator.clipboard.writeText(url)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <button
      type="button"
      onClick={copyLink}
      className="shrink-0 rounded-lg border border-canvas-line-strong bg-canvas-paper px-2.5 py-1.5 text-xs text-canvas-ink transition-colors hover:border-canvas-gold"
    >
      {copied ? 'Copied!' : 'Share'}
    </button>
  )
}

/**
 * The page body below {@link AthenaTopBar}: full-width, matching the
 * Semantic Change Explorer's own edge-to-edge layout (ticket #91's approved
 * mockup has no centered, narrow-card chrome anywhere — that includes the
 * repo/PR picker, not just the Explorer). `narrow` opts a page back into a
 * readable measure for prose-heavy content (e.g. the pre-submission summary)
 * without reintroducing a fixed page-wide max-width for every page.
 */
export function PageShell({
  children,
  narrow = false,
}: {
  children: React.ReactNode
  narrow?: boolean
}) {
  return (
    <div className={`px-6 py-10 sm:px-10 sm:py-14 ${narrow ? 'mx-auto max-w-2xl' : ''}`}>
      <div className="animate-rise-in">{children}</div>
    </div>
  )
}

export function BackLink({ children, onClick }: { children: React.ReactNode; onClick: () => void }) {
  return (
    <button
      type="button"
      className="mb-6 inline-flex items-center gap-1.5 text-sm text-ink-500 transition-colors hover:text-ink-900"
      onClick={onClick}
    >
      {children}
    </button>
  )
}

export function PageHeading({ eyebrow, title }: { eyebrow?: string; title: string }) {
  return (
    <div className="mb-8">
      {eyebrow && <p className="mb-1.5 text-xs font-medium tracking-wide text-ink-500 uppercase">{eyebrow}</p>}
      <h1 className="text-2xl font-semibold text-ink-900">{title}</h1>
    </div>
  )
}

export function Card({
  children,
  className = '',
  as: Tag = 'div',
  onClick,
  role,
  tabIndex,
  onKeyDown,
}: {
  children: React.ReactNode
  className?: string
  as?: 'div' | 'ul'
  onClick?: () => void
  role?: string
  tabIndex?: number
  onKeyDown?: React.KeyboardEventHandler
}) {
  return (
    <Tag
      className={`rounded-xl border border-ink-200 bg-paper-raised shadow-[0_1px_2px_rgba(28,26,23,0.04)] ${className}`}
      onClick={onClick}
      role={role}
      tabIndex={tabIndex}
      onKeyDown={onKeyDown}
    >
      {children}
    </Tag>
  )
}

export function PrimaryButton({
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...props}
      className={`rounded-lg bg-ink-900 px-4 py-2 text-sm font-medium text-white transition-all duration-150 hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40 ${props.className ?? ''}`}
    >
      {children}
    </button>
  )
}

export function SecondaryButton({
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...props}
      className={`rounded-lg border border-ink-200 bg-paper-raised px-3 py-1.5 text-sm font-medium text-ink-700 transition-colors duration-150 hover:border-accent hover:bg-ink-100 disabled:cursor-not-allowed disabled:opacity-40 ${props.className ?? ''}`}
    >
      {children}
    </button>
  )
}

export function SectionLabel({ children }: { children: React.ReactNode }) {
  return <h2 className="mb-3 text-xs font-semibold tracking-wide text-ink-500 uppercase">{children}</h2>
}

/**
 * The unified loading indicator (ticket #109/PR #120), restyled to carry
 * Athena's identity (ticket #123) instead of reading as an unbranded
 * generic spinner: three accent-gold dots pulsing in a stagger, echoing
 * the same calm-motion language as the rest of the app's own pop/rise
 * animations rather than a plain single-dot pulse with no distinguishing
 * character.
 */
export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <PageShell>
      <div role="status" aria-label="Loading" className="flex items-center gap-2 text-sm text-ink-500">
        <span className="flex items-center gap-1">
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-accent [animation-delay:0ms]" />
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-accent [animation-delay:160ms]" />
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-accent [animation-delay:320ms]" />
        </span>
        {label}
      </div>
    </PageShell>
  )
}

/**
 * The full-screen loading take-over for the app's three big waits —
 * fetching repositories, fetching a repository's open PRs, and loading a
 * selected PR into the Semantic Canvas — replacing the small dot-based
 * {@link LoadingState} at exactly those spots so the wait reads as part of
 * Athena's identity rather than a generic spinner. The medallion video
 * already animates itself (a chromatic-aberration "activating" pulse baked
 * into its frames) against a solid black square; clipped to a circle here
 * so only the round medallion shows, floating on the ambient gold aura
 * rather than sitting in a visible dark card. autoPlay+muted+loop+
 * playsInline is what lets a <video> autoplay at all in most browsers
 * (notably iOS Safari, which refuses autoplay on anything with sound).
 */
export function FullScreenLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <div
      role="status"
      aria-label={label}
      className="flex min-h-[70vh] flex-col items-center justify-center gap-6 px-6 py-16 text-center"
    >
      <div className="relative flex h-64 w-64 items-center justify-center sm:h-80 sm:w-80">
        <div
          className="animate-loader-glow absolute inset-0 rounded-full blur-2xl"
          style={{
            background:
              'radial-gradient(circle, var(--color-accent-soft) 0%, var(--color-canvas-gold-glow) 55%, transparent 75%)',
          }}
          aria-hidden="true"
        />
        <video
          autoPlay
          muted
          loop
          playsInline
          aria-hidden="true"
          className="animate-loader-medallion-in relative h-full w-full rounded-full object-cover drop-shadow-[0_18px_40px_rgba(42,38,32,0.18)]"
        >
          <source src="/athena-loading.webm" type="video/webm" />
          <source src="/athena-loading.mp4" type="video/mp4" />
        </video>
      </div>
      <p className="text-sm font-medium tracking-wide text-ink-500">{label}</p>
    </div>
  )
}

export function ErrorState({ message }: { message: string }) {
  return (
    <PageShell>
      <p className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{message}</p>
    </PageShell>
  )
}

/** Renders a unified diff with added/removed lines colored distinctly — the one diff-rendering
 * treatment reused everywhere a diff is shown (`ChangeDetailPage`, the Semantic Change Explorer's
 * evidence panel), rather than each page building its own. */
export function DiffView({ diff }: { diff: string }) {
  if (!diff) {
    return (
      <Card className="overflow-hidden bg-ink-900">
        <pre className="overflow-x-auto p-4 font-mono text-xs leading-relaxed text-ink-100">
          No diff recorded for this Change.
        </pre>
      </Card>
    )
  }

  return (
    <Card className="overflow-hidden bg-ink-900">
      <pre className="overflow-x-auto p-4 font-mono text-xs leading-relaxed">
        {diff.split('\n').map((line, i) => {
          const isAdded = line.startsWith('+')
          const isRemoved = line.startsWith('-')
          const color = isAdded ? 'text-emerald-400' : isRemoved ? 'text-red-400' : 'text-ink-400'
          return (
            <div key={i} className={color}>
              {line || ' '}
            </div>
          )
        })}
      </pre>
    </Card>
  )
}

import { useEffect, useMemo, useRef, useState } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import {
  getCanvasCommentCounts,
  getModuleSemanticProfile,
  getModuleTopology,
  NoPullRequestSelectedError,
  NotConnectedError,
  type ImportedPullRequest,
  type ModuleTerritory,
  type ModuleTopology,
} from './api'
import { AthenaTopBar, ErrorState, FullScreenLoader } from './ui'
import ZoomAltitudeRail, { populatedStops, type AltitudeStop } from './ZoomAltitudeRail'
import ZoomAltitudeContent, { type NodeSelection } from './ZoomAltitudeContent'
import DetailDrawer, { type DrawerSelection } from './DetailDrawer'
import FileFirstMode, { type FileRow } from './FileFirstMode'
import { conceptItemId, fileItemId, territoryItemId } from './canvasItemId'

/**
 * The Semantic Canvas (tickets #129/#130): replaces the Semantic Change
 * Explorer's permanent three-pane shell with a full-viewport pan/zoom
 * surface. On load it shows a territory map — one spatial region per
 * module the PR touches or references — so a reviewer sees where a change
 * physically lands in the system before diving into any one part of it.
 * Diving into a territory reveals the zoom-altitude rail (#130): the six
 * semantic dimensions as literal camera-zoom stops, compressed to only
 * the dimensions that territory actually has content for, landing on the
 * first populated one. The detail drawer/Code content and File-First mode
 * are separate tickets — a selected node currently shows no further detail
 * beyond this altitude's own content.
 */

const MIN_SCALE = 0.4
const MAX_SCALE = 2.5
const ZOOM_STEP = 0.2

interface Camera {
  x: number
  y: number
  scale: number
}

const INITIAL_CAMERA: Camera = { x: 0, y: 0, scale: 1 }

/** A territory's box on the canvas, in canvas-space pixels. */
interface TerritoryBox {
  territory: ModuleTerritory
  x: number
  y: number
  width: number
  height: number
}

const TERRITORY_WIDTH = 320
const TERRITORY_HEIGHT = 170
const TERRITORY_GAP_X = 380
const TERRITORY_GAP_Y = 230
const COLUMNS = 3

function layoutTerritories(topology: ModuleTopology): TerritoryBox[] {
  return topology.territories.map((territory, index) => {
    const column = index % COLUMNS
    const row = Math.floor(index / COLUMNS)
    return {
      territory,
      x: column * TERRITORY_GAP_X,
      y: row * TERRITORY_GAP_Y,
      width: TERRITORY_WIDTH,
      height: TERRITORY_HEIGHT,
    }
  })
}

/**
 * The camera that centers the whole territory map inside the viewport
 * (prototype: the territory grid opens centered, not pinned to the
 * top-left corner where canvas-space (0,0) happens to fall). Falls back
 * to the origin when the viewport hasn't been measured yet (e.g. jsdom in
 * tests, or before the first layout pass) — the same 0-sized rect
 * `diveInto` already tolerates.
 */
function centeredCamera(boxes: TerritoryBox[], viewport: { width: number; height: number }): Camera {
  if (boxes.length === 0 || (viewport.width === 0 && viewport.height === 0)) {
    return INITIAL_CAMERA
  }
  const minX = Math.min(...boxes.map((b) => b.x))
  const minY = Math.min(...boxes.map((b) => b.y))
  const maxX = Math.max(...boxes.map((b) => b.x + b.width))
  const maxY = Math.max(...boxes.map((b) => b.y + b.height))
  const contentWidth = maxX - minX
  const contentHeight = maxY - minY
  const centerX = minX + contentWidth / 2
  const centerY = minY + contentHeight / 2
  return {
    x: viewport.width / 2 - centerX,
    y: viewport.height / 2 - centerY,
    scale: 1,
  }
}

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
}

// Matches the approved prototype's territory heat treatment: a solid gold
// border + faint gold wash for a brand-new module, solid muted-green for a
// touched one, and a dashed idle border — dashed is the DEFAULT territory
// shape (per the prototype), not something reserved for idle alone.
const STATUS_META: Record<ModuleTerritory['status'], { className: string }> = {
  NEW: {
    className:
      'border-2 border-solid border-canvas-territory-new bg-[linear-gradient(180deg,rgba(199,154,62,0.09),rgba(199,154,62,0.02))]',
  },
  TOUCHED: {
    className:
      'border-2 border-solid border-canvas-territory-touched bg-[linear-gradient(180deg,rgba(139,154,115,0.08),rgba(139,154,115,0.02))]',
  },
  IDLE: { className: 'border-2 border-dashed border-canvas-territory-idle bg-canvas-paper-raised opacity-70' },
}

export default function SemanticCanvasPage({
  pullRequest,
  picker,
  onNotConnected,
  onNoPullRequestSelected,
  liveSession,
  liveSessionPanel,
}: {
  pullRequest: ImportedPullRequest | null
  // The repo/PR switcher (ticket #128 follow-up), owned by App.tsx and
  // rendered into this page's own top bar so it stays present while
  // reviewing, not just on a screen that comes before the Canvas.
  picker?: React.ReactNode
  onNotConnected: () => void
  onNoPullRequestSelected: () => void
  // Live Code Review Session sync (ticket #158), both optional so every
  // existing caller/test is unaffected: while presenting, this participant's
  // own territory navigation is reported upward; while following, an
  // incoming shared territory drives this same navigation the way clicking
  // a territory box would. Deliberately territory-grained only, not every
  // zoom-altitude stop/node selection — see the ticket's own "does not need
  // to synchronize every UI interaction" scope note.
  liveSession?: {
    isPresenter: boolean
    isFollowing: boolean
    sharedTerritory: string | undefined
    onLocalTerritoryChange: (moduleName: string | undefined) => void
  }
  // The Live Code Review Session panel (start/join/presence/controls),
  // rendered into this page's own top bar (ticket #158) — owned by App.tsx,
  // the same way `picker` already is.
  liveSessionPanel?: React.ReactNode
}) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['module-topology'],
    queryFn: getModuleTopology,
    retry: false,
  })
  const [camera, setCamera] = useState<Camera>(INITIAL_CAMERA)
  const [focusedTerritory, setFocusedTerritory] = useState<string | undefined>(undefined)
  const [currentStop, setCurrentStop] = useState<AltitudeStop | undefined>(undefined)
  const [showLayerBadges, setShowLayerBadges] = useState(false)
  const [instantTransition, setInstantTransition] = useState(false)
  const [drawerSelection, setDrawerSelection] = useState<DrawerSelection | undefined>(undefined)
  const [reviewMode, setReviewMode] = useState<'CONTEXTUAL' | 'FILE_FIRST'>('CONTEXTUAL')
  // "Show only commented" (ticket #134): dims every canvas item with no
  // comments rather than hiding them — spatial context (where an
  // uncommented item sits relative to a commented one) matters even while
  // scanning for comments.
  const [showCommentedOnly, setShowCommentedOnly] = useState(false)
  // "Explain this" (ticket #132) wants Structure specifically, not just
  // whichever stop happens to be first-populated — set right before diving
  // in, consumed once by the landing effect below, then cleared.
  const forcedLandingStop = useRef<AltitudeStop | undefined>(undefined)
  // "Explain this" (ticket #132) switches to Contextual mode and needs to dive
  // into a territory, but the canvas viewport containerRef.current measures is
  // null until Contextual mode's DOM actually mounts — can't happen in the same
  // synchronous click handler as the mode switch. Queue the target module name
  // instead; the effect below (declared after boxes/diveInto exist) dives in
  // once the viewport has mounted.
  const [pendingExplainTarget, setPendingExplainTarget] = useState<string | undefined>(undefined)
  const dragState = useRef<{ startX: number; startY: number; cameraX: number; cameraY: number } | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  const { data: territoryProfile } = useQuery({
    queryKey: ['module-semantic-profile', focusedTerritory],
    queryFn: () => getModuleSemanticProfile(focusedTerritory!),
    enabled: focusedTerritory !== undefined,
    retry: false,
  })

  // Comment counts per canvas item (ticket #134), for the topbar total and
  // pin badges — fetched once for the whole PR rather than per item, the
  // same "one call covers every item" shape as contextFiles below.
  const { data: commentCounts } = useQuery({
    queryKey: ['canvas-comment-counts'],
    queryFn: getCanvasCommentCounts,
    enabled: data !== undefined,
    retry: false,
  })
  const totalCommentCount = commentCounts ? Object.values(commentCounts).reduce((sum, n) => sum + n, 0) : 0

  // File-First's "Has context" filter (ticket #132) needs to know which files
  // have a matching canvas node across the WHOLE PR, not just the focused
  // territory — fetched lazily (only in File-First mode) across every
  // territory rather than eagerly for every PR, since Contextual mode never
  // needs more than the one focused territory's profile.
  const allTerritoryNames = data?.territories.map((t) => t.moduleName) ?? []
  const allProfileQueries = useQueries({
    queries: allTerritoryNames.map((moduleName) => ({
      queryKey: ['module-semantic-profile', moduleName],
      queryFn: () => getModuleSemanticProfile(moduleName),
      enabled: reviewMode === 'FILE_FIRST',
      retry: false,
    })),
  })
  const contextFiles = useMemo(() => {
    const files = new Set<string>()
    for (const query of allProfileQueries) {
      for (const entry of query.data?.dimensions ?? []) {
        for (const file of entry.filesTouched ?? []) {
          files.add(file)
        }
      }
    }
    return files
  }, [allProfileQueries])

  // Land on the territory's first populated altitude stop (ticket #130),
  // not a fixed default — re-runs whenever a new territory's profile arrives.
  // "Explain this" (ticket #132) can override this once with a specific stop.
  useEffect(() => {
    if (territoryProfile) {
      const stops = populatedStops(territoryProfile)
      const forced = forcedLandingStop.current
      forcedLandingStop.current = undefined
      setCurrentStop(forced && stops.includes(forced) ? forced : stops[0])
    } else {
      setCurrentStop(undefined)
    }
  }, [territoryProfile])

  // "Explain this" (ticket #132): once Contextual mode's viewport has
  // actually mounted (containerRef.current is null until then), dive into
  // the queued target module the same way clicking its territory would.
  // diveInto is a hoisted function declaration further down this same
  // component body, so calling it here (before its textual definition) is
  // valid — only the effect's own registration needs to happen up here,
  // above the early-return guards, to satisfy the Rules of Hooks.
  useEffect(() => {
    if (!pendingExplainTarget || !containerRef.current || !data) {
      return
    }
    const box = layoutTerritories(data).find((b) => b.territory.moduleName === pendingExplainTarget)
    if (box) {
      forcedLandingStop.current = 'STRUCTURE'
      diveInto(box)
    }
    setPendingExplainTarget(undefined)
  }, [pendingExplainTarget, data])

  // Live Code Review Session sync (ticket #158): while presenting, report
  // this participant's own territory navigation upward so it becomes the
  // session's shared focus for everyone else.
  useEffect(() => {
    if (liveSession?.isPresenter) {
      liveSession.onLocalTerritoryChange(focusedTerritory)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [liveSession?.isPresenter, focusedTerritory])

  // Live Code Review Session sync (ticket #158): while following, an
  // incoming shared territory drives this participant's own navigation the
  // same way clicking that territory box would — diveInto/layoutTerritories
  // are hoisted/module-level, so calling them here (before their textual
  // definition, above the early-return guards) is valid, the same reasoning
  // as the "Explain this" effect just above.
  useEffect(() => {
    if (!liveSession?.isFollowing || liveSession.sharedTerritory === focusedTerritory) {
      return
    }
    if (liveSession.sharedTerritory === undefined) {
      resetCamera()
      return
    }
    if (!data) {
      return
    }
    const box = layoutTerritories(data).find((b) => b.territory.moduleName === liveSession.sharedTerritory)
    if (box) {
      diveInto(box)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [liveSession?.isFollowing, liveSession?.sharedTerritory, data])

  // Center the territory map in the viewport on first load (prototype:
  // the map opens centered, never pinned to canvas-space (0,0) at the
  // top-left) — runs once the container has real dimensions and only
  // while still at the untouched initial camera, so it never fights a
  // reviewer's own pan/zoom or a dive-in that's already underway.
  useEffect(() => {
    if (!containerRef.current || !data || focusedTerritory !== undefined) {
      return
    }
    setCamera((current) => {
      if (current.x !== INITIAL_CAMERA.x || current.y !== INITIAL_CAMERA.y || current.scale !== INITIAL_CAMERA.scale) {
        return current
      }
      const viewport = containerRef.current!.getBoundingClientRect()
      return centeredCamera(layoutTerritories(data), viewport)
    })
  }, [data, focusedTerritory])

  if (isError) {
    if (error instanceof NotConnectedError) {
      onNotConnected()
      return null
    }
    if (error instanceof NoPullRequestSelectedError) {
      onNoPullRequestSelected()
      return null
    }
    return (
      <div className="flex h-screen w-full flex-col overflow-hidden bg-canvas-paper text-canvas-ink">
        <CanvasIdentityBar pullRequest={pullRequest} picker={picker} />
        <ErrorState message="Could not load the Semantic Canvas." />
      </div>
    )
  }
  if (isLoading || !data) {
    return (
      <div className="flex h-screen w-full flex-col overflow-hidden bg-canvas-paper text-canvas-ink">
        <CanvasIdentityBar pullRequest={pullRequest} picker={picker} />
        <FullScreenLoader label="Loading the Semantic Canvas…" />
      </div>
    )
  }
  // Narrowed once here so functions declared below (closures TypeScript can't
  // narrow `data` through) can reference a value it knows is always defined.
  const topology = data

  const boxes = layoutTerritories(topology)

  function clampScale(scale: number): number {
    return Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale))
  }

  function diveInto(box: TerritoryBox) {
    if (!containerRef.current) {
      return
    }
    const viewport = containerRef.current.getBoundingClientRect()
    // Fill nearly the entire viewport: scale so the territory's box occupies
    // most of the available width/height, then center it.
    const targetScale = clampScale(
      Math.min((viewport.width * 0.92) / box.width, (viewport.height * 0.92) / box.height),
    )
    const centerX = box.x + box.width / 2
    const centerY = box.y + box.height / 2
    setInstantTransition(prefersReducedMotion())
    setCamera({
      x: viewport.width / 2 - centerX * targetScale,
      y: viewport.height / 2 - centerY * targetScale,
      scale: targetScale,
    })
    setFocusedTerritory(box.territory.moduleName)
  }

  function resetCamera() {
    setInstantTransition(prefersReducedMotion())
    const viewport = containerRef.current?.getBoundingClientRect() ?? { width: 0, height: 0 }
    setCamera(centeredCamera(boxes, viewport))
    setFocusedTerritory(undefined)
  }

  function zoomBy(delta: number) {
    setInstantTransition(false)
    setCamera((current) => ({ ...current, scale: clampScale(current.scale + delta) }))
  }

  function onWheel(e: React.WheelEvent) {
    e.preventDefault()
    setInstantTransition(true)
    const delta = e.deltaY > 0 ? -ZOOM_STEP : ZOOM_STEP
    setCamera((current) => ({ ...current, scale: clampScale(current.scale + delta) }))
  }

  function onPointerDown(e: React.PointerEvent) {
    dragState.current = { startX: e.clientX, startY: e.clientY, cameraX: camera.x, cameraY: camera.y }
  }

  function onPointerMove(e: React.PointerEvent) {
    const drag = dragState.current
    if (!drag) {
      return
    }
    setInstantTransition(true)
    const dx = e.clientX - drag.startX
    const dy = e.clientY - drag.startY
    // Capture drag into a local const rather than re-reading dragState.current
    // inside the updater: React may invoke a setState updater later/more than
    // once (e.g. StrictMode's dev double-render), by which point a concurrent
    // onPointerUp could have already nulled the ref out from under it.
    setCamera((current) => ({ ...current, x: drag.cameraX + dx, y: drag.cameraY + dy }))
  }

  function onPointerUp() {
    dragState.current = null
  }

  function selectStop(stop: AltitudeStop) {
    setInstantTransition(false)
    setCurrentStop(stop)
  }

  // Per-node-kind zoom-in on selection (ticket #130): a file/symbol node
  // (denser, more to read) zooms in further than a concept card. Capped at
  // MAX_SCALE so repeated clicks can't compound into an unreadably close
  // view — each selection sets scale to a fixed target for its node kind
  // rather than adding a delta on top of the current scale, so clicking
  // several nodes in a row never stacks zoom beyond that kind's own cap.
  // Also opens the detail drawer (ticket #131) with that node's content —
  // opening it never itself touches camera state, so the reviewer's pan/
  // zoom position survives the drawer opening independent of this zoom.
  function selectNode(selection: NodeSelection) {
    setInstantTransition(false)
    const targetScale = selection.kind === 'file' ? MAX_SCALE : Math.min(MAX_SCALE, INITIAL_CAMERA.scale + 0.6)
    setCamera((current) => ({ ...current, scale: clampScale(targetScale) }))
    if (selection.kind === 'concept') {
      setDrawerSelection({
        kind: 'concept',
        entry: selection.entry,
        itemId: conceptItemId(focusedTerritory!, selection.entry.conceptName),
      })
    } else {
      const territory = topology.territories.find((t) => t.moduleName === focusedTerritory)
      setDrawerSelection({
        kind: 'file',
        fileName: selection.fileName,
        fromConceptName: selection.owningEntry.conceptName,
        changeKeys: territory?.changeKeys ?? [],
        itemId: fileItemId(focusedTerritory!, selection.fileName),
      })
    }
  }

  // Jumping to a file from a concept's linked chip (ticket #131) moves the
  // camera to the Structure altitude, where that file's own node lives —
  // clears the drawer selection but never touches camera pan/zoom itself.
  function jumpToFile() {
    setDrawerSelection(undefined)
    setCurrentStop('STRUCTURE')
  }

  function openOverviewDrawer() {
    setDrawerSelection({ kind: 'overview' })
  }

  // Opens a canvas item's comment thread (ticket #134) — clicking a pin
  // badge, or the drawer's own Comment affordance for an item with none yet.
  function openCommentsDrawer(itemId: string, itemLabel: string) {
    setDrawerSelection({ kind: 'comments', itemId, itemLabel })
  }

  // File-First's click-to-diff (ticket #132): opens the same shared detail
  // drawer File-node content the canvas itself uses — no second diff surface.
  function openFileFromFileFirst(row: FileRow) {
    setDrawerSelection({
      kind: 'file',
      fileName: row.fileName,
      fromConceptName: row.moduleName,
      changeKeys: [row.changeKey],
      itemId: fileItemId(row.moduleName, row.fileName),
    })
  }

  // "Explain this" (ticket #132): switches to Contextual mode, dives into
  // the file's module territory, and lands on its Structure-altitude node —
  // semantic context on demand rather than forced by default.
  function explainFile(row: FileRow) {
    setReviewMode('CONTEXTUAL')
    setPendingExplainTarget(row.moduleName)
  }

  const byName = new Map(boxes.map((box) => [box.territory.moduleName, box]))

  return (
    <div className="flex h-screen w-full flex-col overflow-hidden bg-canvas-paper text-canvas-ink" data-testid="semantic-canvas">
      <CanvasTopBar
        pullRequest={pullRequest}
        picker={picker}
        liveSessionPanel={liveSessionPanel}
        reviewMode={reviewMode}
        onSelectReviewMode={setReviewMode}
        totalCommentCount={totalCommentCount}
        showCommentedOnly={showCommentedOnly}
        onToggleCommentedOnly={() => setShowCommentedOnly((current) => !current)}
      />
      <div className="flex min-h-0 flex-1">
        {reviewMode === 'CONTEXTUAL' && (
          <CanvasSidebar
            hasFocusedTerritory={focusedTerritory !== undefined}
            focusedTerritoryName={focusedTerritory}
            territoryProfile={territoryProfile}
            currentStop={currentStop}
            onSelectStop={selectStop}
            onOpenOverview={openOverviewDrawer}
            onZoomOut={resetCamera}
          />
        )}
        {reviewMode === 'FILE_FIRST' ? (
          <FileFirstMode
            topology={topology}
            contextFiles={contextFiles}
            onOpenFile={openFileFromFileFirst}
            onExplainFile={explainFile}
          />
        ) : (
          <div
            className="relative flex-1 overflow-hidden bg-canvas-paper bg-[radial-gradient(var(--color-canvas-dot)_1.2px,transparent_1.2px)] bg-[length:26px_26px]"
          >
            <div
              ref={containerRef}
              role="application"
              aria-label="Semantic Canvas territory map"
              className="h-full w-full cursor-grab touch-none"
              onWheel={onWheel}
              onPointerDown={onPointerDown}
              onPointerMove={onPointerMove}
              onPointerUp={onPointerUp}
              onPointerLeave={onPointerUp}
            >
              <div
                className={instantTransition ? '' : 'transition-transform duration-500 ease-[cubic-bezier(0.22,1,0.36,1)]'}
                style={{
                  transform: `translate(${camera.x}px, ${camera.y}px) scale(${camera.scale})`,
                  transformOrigin: '0 0',
                  position: 'relative',
                  width: 0,
                  height: 0,
                }}
              >
                <svg className="pointer-events-none absolute left-0 top-0 overflow-visible" width={1} height={1}>
                  {topology.dependencies.map((dependency) => {
                    const from = byName.get(dependency.from)
                    const to = byName.get(dependency.to)
                    if (!from || !to) {
                      return null
                    }
                    // A rail touching the focused territory animates a flowing dash
                    // to read as "this connection is active" (ticket #133); an
                    // unrelated rail stays a plain static line.
                    const touchesSelection =
                      focusedTerritory !== undefined &&
                      (dependency.from === focusedTerritory || dependency.to === focusedTerritory)
                    return (
                      <line
                        key={`${dependency.from}->${dependency.to}`}
                        data-testid="dependency-rail"
                        data-from={dependency.from}
                        data-to={dependency.to}
                        data-active={touchesSelection ? 'true' : undefined}
                        x1={from.x + from.width / 2}
                        y1={from.y + from.height / 2}
                        x2={to.x + to.width / 2}
                        y2={to.y + to.height / 2}
                        stroke={touchesSelection ? 'var(--color-canvas-gold)' : 'var(--color-canvas-line-strong)'}
                        strokeWidth={touchesSelection ? 2.5 : 1.5}
                        strokeDasharray={touchesSelection ? '6 6' : undefined}
                        className={touchesSelection ? 'animate-flow-dash' : undefined}
                      />
                    )
                  })}
                </svg>
                {boxes.map((box) => (
                  <TerritoryCard
                    key={box.territory.moduleName}
                    box={box}
                    focused={focusedTerritory === box.territory.moduleName}
                    dependencies={topology.dependencies}
                    commentCount={commentCounts?.[territoryItemId(box.territory.moduleName)] ?? 0}
                    dimmed={showCommentedOnly && !commentCounts?.[territoryItemId(box.territory.moduleName)]}
                    onClick={() => diveInto(box)}
                    onOpenComments={() => openCommentsDrawer(territoryItemId(box.territory.moduleName), box.territory.moduleName)}
                  />
                ))}
              </div>
            </div>
            <div className="absolute bottom-6 right-6 flex gap-2">
              <button
                aria-label="Zoom in"
                className="rounded-full border border-canvas-line-strong bg-canvas-paper-raised px-3 py-2 text-sm text-canvas-ink-soft shadow-[var(--shadow-canvas)] hover:border-canvas-gold"
                onClick={() => zoomBy(ZOOM_STEP)}
              >
                +
              </button>
              <button
                aria-label="Zoom out"
                className="rounded-full border border-canvas-line-strong bg-canvas-paper-raised px-3 py-2 text-sm text-canvas-ink-soft shadow-[var(--shadow-canvas)] hover:border-canvas-gold"
                onClick={() => zoomBy(-ZOOM_STEP)}
              >
                −
              </button>
              <button
                aria-label="Reset view"
                className="rounded-full border border-canvas-line-strong bg-canvas-paper-raised px-3 py-2 text-sm text-canvas-ink-soft shadow-[var(--shadow-canvas)] hover:border-canvas-gold"
                onClick={resetCamera}
              >
                Reset
              </button>
            </div>
            {!focusedTerritory && (
              <button
                type="button"
                aria-label="PR overview"
                className="absolute left-1/2 top-6 max-w-[280px] -translate-x-1/2 rounded-2xl border border-canvas-gold bg-canvas-gold-soft px-5 py-4 text-center shadow-[var(--shadow-canvas)] hover:shadow-[var(--shadow-canvas-lift)]"
                onClick={openOverviewDrawer}
              >
                <span className="block font-display text-base font-semibold leading-tight text-canvas-gold-deep">
                  {topology.territories.length} module{topology.territories.length === 1 ? '' : 's'} touched
                </span>
              </button>
            )}
          </div>
        )}
      </div>
      {reviewMode === 'CONTEXTUAL' && focusedTerritory && territoryProfile && currentStop && (
        <div className="absolute inset-x-0 bottom-0 top-[63px] left-44 flex flex-col overflow-hidden border-t border-canvas-line bg-canvas-paper-raised">
          <div className="flex items-center justify-between border-b border-canvas-line px-5 py-2.5">
            <span className="font-display text-base font-semibold text-canvas-ink">{focusedTerritory}</span>
            <button
              type="button"
              aria-pressed={showLayerBadges}
              className={`rounded-lg border px-3 py-1.5 text-xs font-semibold ${
                showLayerBadges
                  ? 'border-canvas-gold bg-canvas-gold-soft text-canvas-gold-deep'
                  : 'border-canvas-line-strong bg-canvas-paper-raised text-canvas-ink-soft hover:border-canvas-gold'
              }`}
              onClick={() => setShowLayerBadges((current) => !current)}
            >
              Semantic layers
            </button>
          </div>
          <div className="flex-1 overflow-auto">
            <ZoomAltitudeContent
              key={currentStop}
              stop={currentStop}
              profile={territoryProfile}
              showLayerBadges={showLayerBadges}
              onSelectNode={selectNode}
              moduleName={focusedTerritory}
              commentCounts={commentCounts ?? {}}
              showCommentedOnly={showCommentedOnly}
              onOpenComments={openCommentsDrawer}
            />
          </div>
        </div>
      )}
      <DetailDrawer
        selection={drawerSelection}
        topology={topology}
        onClose={() => setDrawerSelection(undefined)}
        onJumpToFile={jumpToFile}
        onOpenComments={openCommentsDrawer}
      />
    </div>
  )
}

/**
 * The PR-identity chip shown in the top bar's gold pill — shared by {@link
 * CanvasTopBar} and {@link CanvasIdentityBar} so the loading/error states
 * (which have no review-mode/comment controls to show yet) still render the
 * exact same chip as the fully-loaded canvas, instead of a bare bar.
 */
function prChipFor(pullRequest: ImportedPullRequest | null) {
  return (
    pullRequest && (
      <span className="inline-flex min-w-0 items-center gap-1.5 whitespace-nowrap rounded-full bg-canvas-gold-soft py-1 pl-2.5 pr-3 text-xs text-canvas-ink-soft">
        <span className="font-mono font-semibold text-canvas-gold-deep">#{pullRequest.number}</span>
        <span className="overflow-hidden text-ellipsis">{pullRequest.title}</span>
      </span>
    )
  )
}

/**
 * The bar shown while the canvas is loading or has failed to load (ticket
 * #128 follow-up): logo + PR chip only, no review-mode/comments controls
 * since there's no data yet for them to act on — kept distinct from
 * {@link CanvasTopBar} rather than passing it dummy handlers.
 */
function CanvasIdentityBar({
  pullRequest,
  picker,
}: {
  pullRequest: ImportedPullRequest | null
  picker?: React.ReactNode
}) {
  return <AthenaTopBar prChip={prChipFor(pullRequest)} picker={picker} />
}

/**
 * The top bar (ticket #128's approved prototype): the shared {@link
 * AthenaTopBar} plus this screen's own PR chip and Contextual/File-First
 * mode select — previously missing entirely; the mode toggle floated as an
 * unstyled pill with no logo, PR identity, or comments affordance anywhere
 * on screen.
 */
function CanvasTopBar({
  pullRequest,
  picker,
  liveSessionPanel,
  reviewMode,
  onSelectReviewMode,
  totalCommentCount,
  showCommentedOnly,
  onToggleCommentedOnly,
}: {
  pullRequest: ImportedPullRequest | null
  picker?: React.ReactNode
  liveSessionPanel?: React.ReactNode
  reviewMode: 'CONTEXTUAL' | 'FILE_FIRST'
  onSelectReviewMode: (mode: 'CONTEXTUAL' | 'FILE_FIRST') => void
  totalCommentCount: number
  showCommentedOnly: boolean
  onToggleCommentedOnly: () => void
}) {
  return (
    <AthenaTopBar
      prChip={prChipFor(pullRequest)}
      picker={picker}
      right={
        <>
          {liveSessionPanel}
          <button
            type="button"
            aria-pressed={showCommentedOnly}
            title="Show only items with comments"
            onClick={onToggleCommentedOnly}
            className={`rounded-lg border px-3 py-1.5 text-xs font-semibold ${
              showCommentedOnly
                ? 'border-canvas-gold bg-canvas-gold-soft text-canvas-gold-deep'
                : 'border-canvas-line-strong bg-canvas-paper-raised text-canvas-ink-soft hover:border-canvas-gold'
            }`}
          >
            {totalCommentCount} comment{totalCommentCount === 1 ? '' : 's'}
          </button>
          <label className="flex items-center gap-1.5 text-xs text-canvas-ink-soft">
            Review mode
            <select
              aria-label="Review mode"
              value={reviewMode}
              onChange={(e) => onSelectReviewMode(e.target.value as 'CONTEXTUAL' | 'FILE_FIRST')}
              className="rounded-lg border border-canvas-line-strong bg-canvas-paper-raised px-2.5 py-1.5 text-xs text-canvas-ink"
            >
              <option value="CONTEXTUAL">Contextual</option>
              <option value="FILE_FIRST">File-First</option>
            </select>
          </label>
        </>
      }
    />
  )
}

/**
 * The left sidebar (ticket #128's approved prototype): a plain nav stack
 * plus a "Where you are" position indicator that is always present, not
 * just once a territory is focused — the prototype's rail shows this stack
 * with "This PR" as the (only, but still visible) step on the very first
 * screen, then appends the focused territory and its zoom-altitude ladder
 * once the reviewer dives in. Previously there was no sidebar at all, and
 * the altitude rail floated as an unstyled top-left box only while a
 * territory was focused, so the first screen showed no position at all.
 */
function CanvasSidebar({
  hasFocusedTerritory,
  focusedTerritoryName,
  territoryProfile,
  currentStop,
  onSelectStop,
  onOpenOverview,
  onZoomOut,
}: {
  hasFocusedTerritory: boolean
  focusedTerritoryName: string | undefined
  territoryProfile: Parameters<typeof ZoomAltitudeRail>[0]['profile'] | undefined
  currentStop: AltitudeStop | undefined
  onSelectStop: (stop: AltitudeStop) => void
  onOpenOverview: () => void
  onZoomOut: () => void
}) {
  return (
    <nav className="flex w-44 flex-shrink-0 flex-col gap-0.5 border-r border-canvas-line bg-canvas-paper-raised p-2.5">
      <button
        type="button"
        onClick={onOpenOverview}
        className="w-full rounded-lg px-2.5 py-2 text-left text-sm text-canvas-ink-soft hover:bg-canvas-gold-soft hover:text-canvas-ink"
      >
        PR Overview
      </button>
      <div className="my-2 h-px bg-canvas-line" />
      <WhereYouAreRail
        focusedTerritoryName={hasFocusedTerritory ? focusedTerritoryName : undefined}
        territoryProfile={territoryProfile}
        currentStop={currentStop}
        onSelectStop={onSelectStop}
        onZoomToTerritory={onZoomOut}
      />
      {hasFocusedTerritory && (
        <button
          type="button"
          onClick={onZoomOut}
          className="mt-2 flex items-center gap-1.5 rounded-lg border border-canvas-gold bg-canvas-gold-soft px-2.5 py-2 text-xs font-semibold text-canvas-gold-deep hover:shadow-[var(--shadow-canvas)]"
        >
          Zoom out
        </button>
      )}
    </nav>
  )
}

/**
 * "Where you are" (ticket #128's approved prototype): always visible, not
 * only once a territory is focused. On the first screen it's a single "This
 * PR" step; once the reviewer dives into a territory, that step becomes
 * clickable (zooms back out) and the territory's own zoom-altitude ladder
 * (Intent → Structure, compressed to only its populated stops) continues
 * beneath it as the next level of the same position indicator.
 */
function WhereYouAreRail({
  focusedTerritoryName,
  territoryProfile,
  currentStop,
  onSelectStop,
  onZoomToTerritory,
}: {
  focusedTerritoryName: string | undefined
  territoryProfile: Parameters<typeof ZoomAltitudeRail>[0]['profile'] | undefined
  currentStop: AltitudeStop | undefined
  onSelectStop: (stop: AltitudeStop) => void
  onZoomToTerritory: () => void
}) {
  const stops = territoryProfile ? populatedStops(territoryProfile) : []
  return (
    <div className="px-1 pt-1">
      <span className="mb-1.5 block px-1.5 text-[10px] font-semibold uppercase tracking-wide text-canvas-ink-faint">
        Where you are
      </span>
      <nav aria-label="Zoom path" className="flex flex-col">
        <PositionStep
          label="This PR"
          hint="every affected module"
          isCurrent={!focusedTerritoryName}
          isPassed={!!focusedTerritoryName}
          onClick={focusedTerritoryName ? onZoomToTerritory : undefined}
        />
        {focusedTerritoryName && (
          <PositionStep label={focusedTerritoryName} hint="this module, maximized" isCurrent={stops.length === 0} isPassed={stops.length > 0} />
        )}
      </nav>
      {focusedTerritoryName && territoryProfile && (
        <ZoomAltitudeRail profile={territoryProfile} currentStop={currentStop} onSelectStop={onSelectStop} nested />
      )}
    </div>
  )
}

/** One step of the "Where you are" ladder — a ring, connecting rail lines, and a label/hint pair. */
function PositionStep({
  label,
  hint,
  isCurrent,
  isPassed = false,
  onClick,
}: {
  label: string
  hint: string
  isCurrent: boolean
  isPassed?: boolean
  onClick?: () => void
}) {
  return (
    <button
      type="button"
      aria-current={isCurrent ? 'true' : undefined}
      disabled={!onClick}
      onClick={onClick}
      className={`relative flex w-full items-center gap-2 rounded-lg px-1.5 py-1.5 text-left ${
        isCurrent ? 'font-semibold text-canvas-gold-deep' : 'text-canvas-ink-faint'
      } ${onClick ? 'hover:bg-canvas-gold-soft' : 'cursor-default'}`}
    >
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
        <span className="text-xs">{label}</span>
        <span className="text-[9.5px] font-normal text-canvas-ink-faint">{hint}</span>
      </span>
    </button>
  )
}

function TerritoryCard({
  box,
  focused,
  dependencies,
  commentCount,
  dimmed,
  onClick,
  onOpenComments,
}: {
  box: TerritoryBox
  focused: boolean
  dependencies: ModuleTopology['dependencies']
  commentCount: number
  dimmed: boolean
  onClick: () => void
  onOpenComments: () => void
}) {
  const { territory } = box
  const meta = STATUS_META[territory.status]
  return (
    <div
      className={`absolute transition-opacity ${dimmed ? 'opacity-35' : ''}`}
      style={{ left: box.x, top: box.y, width: box.width, height: box.height }}
    >
      <button
        type="button"
        role="button"
        aria-label={`${territory.moduleName} territory`}
        data-testid="territory"
        data-module-name={territory.moduleName}
        data-status={territory.status}
        aria-current={focused ? 'true' : undefined}
        onClick={onClick}
        className={`group relative flex h-full w-full flex-col justify-between rounded-[20px] p-4 pb-3 text-left transition-[box-shadow,transform] hover:-translate-y-0.5 hover:shadow-[var(--shadow-canvas-lift)] ${meta.className}`}
      >
        {territory.status === 'NEW' && (
          <div className="pointer-events-none absolute inset-0 rounded-[20px] animate-canvas-territory-pulse motion-reduce:animate-none" />
        )}
        <div>
          <span className="mb-2 inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border border-canvas-line-strong bg-canvas-paper px-2.5 py-1 text-[11px] font-semibold text-canvas-ink-soft">
            {techStackIcon(territory.techStack)}
            {territory.techStackLabel}
          </span>
          <div className="mb-1 flex items-center gap-2 font-display text-[15px] font-semibold leading-tight text-canvas-ink-soft">
            <span
              className="h-2.5 w-2.5 flex-shrink-0 rounded-full"
              style={{ background: heatColor(territory.status) }}
            />
            <span>{territory.moduleName}</span>
          </div>
          <div className="font-mono text-[11px] text-canvas-ink-faint">{territory.statusSummary}</div>
        </div>
        <RelatedTerritoryChips territoryId={territory.moduleName} dependencies={dependencies} />
      </button>
      {commentCount > 0 && (
        <button
          type="button"
          aria-label={`${commentCount} comment${commentCount === 1 ? '' : 's'} on ${territory.moduleName}`}
          data-testid="comment-pin"
          onClick={(e) => {
            e.stopPropagation()
            onOpenComments()
          }}
          className="absolute bottom-3 right-4 z-[3] flex items-center gap-1 rounded-full border border-canvas-gold bg-canvas-gold-soft px-2 py-0.5 text-[11px] font-bold text-canvas-gold-deep hover:shadow-[var(--shadow-canvas)]"
        >
          💬 {commentCount}
        </button>
      )}
    </div>
  )
}

function RelatedTerritoryChips({
  territoryId,
  dependencies,
}: {
  territoryId: string
  dependencies: ModuleTopology['dependencies']
}) {
  const related = dependencies
    .filter((d) => d.from === territoryId || d.to === territoryId)
    .map((d) => (d.from === territoryId ? d.to : d.from))
  if (related.length === 0) {
    return null
  }
  return (
    <div className="mt-auto flex flex-wrap gap-1.5 pt-3.5">
      {related.map((name) => (
        <span
          key={name}
          className="inline-flex items-center gap-1 rounded-full border border-canvas-line bg-canvas-paper px-2 py-0.5 text-[10.5px] text-canvas-ink-faint"
        >
          {name}
        </span>
      ))}
    </div>
  )
}

function heatColor(status: ModuleTerritory['status']): string {
  if (status === 'NEW') return 'var(--color-canvas-territory-new)'
  if (status === 'TOUCHED') return 'var(--color-canvas-territory-touched)'
  return 'var(--color-canvas-territory-idle)'
}

function techStackIcon(techStack: ModuleTerritory['techStack']) {
  if (techStack === 'SPRING_BOOT_JAVA') {
    return (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 2a10 10 0 1 0 7.07 17.07" />
        <path d="M12 2a10 10 0 0 1 7.07 17.07" />
        <path d="M12 12 20 4" />
        <path d="M15 3.5 20 4l.5 5" />
      </svg>
    )
  }
  if (techStack === 'REACT_TYPESCRIPT') {
    return (
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
        <circle cx="12" cy="12" r="2.2" fill="currentColor" stroke="none" />
        <ellipse cx="12" cy="12" rx="10" ry="4.2" />
        <ellipse cx="12" cy="12" rx="10" ry="4.2" transform="rotate(60 12 12)" />
        <ellipse cx="12" cy="12" rx="10" ry="4.2" transform="rotate(120 12 12)" />
      </svg>
    )
  }
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M8 3c-2 2-2 4 0 6s2 4 0 6" />
      <path d="M14 3c-2 2-2 4 0 6s2 4 0 6" />
      <path d="M5 19h14" />
    </svg>
  )
}

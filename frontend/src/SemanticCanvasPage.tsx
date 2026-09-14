import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  getModuleSemanticProfile,
  getModuleTopology,
  NoPullRequestSelectedError,
  NotConnectedError,
  type ModuleTerritory,
  type ModuleTopology,
} from './api'
import { ErrorState, LoadingState } from './ui'
import ZoomAltitudeRail, { populatedStops, type AltitudeStop } from './ZoomAltitudeRail'
import ZoomAltitudeContent, { type NodeSelection } from './ZoomAltitudeContent'
import DetailDrawer, { type DrawerSelection } from './DetailDrawer'

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

const TERRITORY_WIDTH = 260
const TERRITORY_HEIGHT = 160
const TERRITORY_GAP_X = 340
const TERRITORY_GAP_Y = 220
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

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
}

const STATUS_META: Record<ModuleTerritory['status'], { label: string; className: string }> = {
  NEW: { label: 'New', className: 'border-2 border-accent bg-accent-soft animate-pulse' },
  TOUCHED: { label: 'Touched', className: 'border border-ink-300 bg-paper-raised' },
  IDLE: { label: 'Idle', className: 'border border-dashed border-ink-200 bg-paper opacity-60' },
}

export default function SemanticCanvasPage({
  onNotConnected,
  onNoPullRequestSelected,
}: {
  onNotConnected: () => void
  onNoPullRequestSelected: () => void
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
  const dragState = useRef<{ startX: number; startY: number; cameraX: number; cameraY: number } | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  const { data: territoryProfile } = useQuery({
    queryKey: ['module-semantic-profile', focusedTerritory],
    queryFn: () => getModuleSemanticProfile(focusedTerritory!),
    enabled: focusedTerritory !== undefined,
    retry: false,
  })

  // Land on the territory's first populated altitude stop (ticket #130),
  // not a fixed default — re-runs whenever a new territory's profile arrives.
  useEffect(() => {
    if (territoryProfile) {
      const stops = populatedStops(territoryProfile)
      setCurrentStop(stops[0])
    } else {
      setCurrentStop(undefined)
    }
  }, [territoryProfile])

  if (isError) {
    if (error instanceof NotConnectedError) {
      onNotConnected()
      return null
    }
    if (error instanceof NoPullRequestSelectedError) {
      onNoPullRequestSelected()
      return null
    }
    return <ErrorState message="Could not load the Semantic Canvas." />
  }
  if (isLoading || !data) {
    return <LoadingState />
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
    setCamera(INITIAL_CAMERA)
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
    if (!dragState.current) {
      return
    }
    setInstantTransition(true)
    const dx = e.clientX - dragState.current.startX
    const dy = e.clientY - dragState.current.startY
    setCamera((current) => ({ ...current, x: dragState.current!.cameraX + dx, y: dragState.current!.cameraY + dy }))
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
      setDrawerSelection({ kind: 'concept', entry: selection.entry })
    } else {
      const territory = topology.territories.find((t) => t.moduleName === focusedTerritory)
      setDrawerSelection({
        kind: 'file',
        fileName: selection.fileName,
        fromConceptName: selection.owningEntry.conceptName,
        changeKeys: territory?.changeKeys ?? [],
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

  const byName = new Map(boxes.map((box) => [box.territory.moduleName, box]))

  return (
    <div className="relative h-screen w-full overflow-hidden bg-paper" data-testid="semantic-canvas">
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
              return (
                <line
                  key={`${dependency.from}->${dependency.to}`}
                  data-testid="dependency-rail"
                  data-from={dependency.from}
                  data-to={dependency.to}
                  x1={from.x + from.width / 2}
                  y1={from.y + from.height / 2}
                  x2={to.x + to.width / 2}
                  y2={to.y + to.height / 2}
                  stroke="var(--color-ink-300)"
                  strokeWidth={2}
                />
              )
            })}
          </svg>
          {boxes.map((box) => (
            <TerritoryCard
              key={box.territory.moduleName}
              box={box}
              focused={focusedTerritory === box.territory.moduleName}
              onClick={() => diveInto(box)}
            />
          ))}
        </div>
      </div>
      <div className="absolute bottom-6 right-6 flex gap-2">
        <button
          aria-label="Zoom in"
          className="rounded-full border border-ink-200 bg-paper-raised px-3 py-2 text-sm shadow-sm hover:bg-ink-100"
          onClick={() => zoomBy(ZOOM_STEP)}
        >
          +
        </button>
        <button
          aria-label="Zoom out"
          className="rounded-full border border-ink-200 bg-paper-raised px-3 py-2 text-sm shadow-sm hover:bg-ink-100"
          onClick={() => zoomBy(-ZOOM_STEP)}
        >
          −
        </button>
        <button
          aria-label="Reset view"
          className="rounded-full border border-ink-200 bg-paper-raised px-3 py-2 text-sm shadow-sm hover:bg-ink-100"
          onClick={resetCamera}
        >
          Reset
        </button>
        {focusedTerritory && territoryProfile && (
          <button
            aria-pressed={showLayerBadges}
            className={`rounded-full border px-3 py-2 text-sm shadow-sm ${
              showLayerBadges ? 'border-accent bg-accent-soft text-accent' : 'border-ink-200 bg-paper-raised hover:bg-ink-100'
            }`}
            onClick={() => setShowLayerBadges((current) => !current)}
          >
            Semantic layers
          </button>
        )}
      </div>
      {!focusedTerritory && (
        <button
          type="button"
          aria-label="PR overview"
          className="absolute left-6 top-6 rounded-2xl border border-ink-200 bg-paper-raised px-4 py-3 text-left shadow-sm hover:bg-ink-100"
          onClick={openOverviewDrawer}
        >
          <span className="block text-xs uppercase tracking-wide text-ink-500">This PR</span>
          <span className="block font-display text-lg text-ink-900">{topology.territories.length} modules touched</span>
        </button>
      )}
      {focusedTerritory && territoryProfile && (
        <ZoomAltitudeRail profile={territoryProfile} currentStop={currentStop} onSelectStop={selectStop} />
      )}
      {focusedTerritory && territoryProfile && currentStop && (
        <div className="absolute inset-x-0 bottom-0 top-24 overflow-auto">
          <ZoomAltitudeContent
            stop={currentStop}
            profile={territoryProfile}
            showLayerBadges={showLayerBadges}
            onSelectNode={selectNode}
          />
        </div>
      )}
      <DetailDrawer
        selection={drawerSelection}
        topology={topology}
        onClose={() => setDrawerSelection(undefined)}
        onJumpToFile={jumpToFile}
      />
    </div>
  )
}

function TerritoryCard({
  box,
  focused,
  onClick,
}: {
  box: TerritoryBox
  focused: boolean
  onClick: () => void
}) {
  const { territory } = box
  const meta = STATUS_META[territory.status]
  return (
    <button
      type="button"
      role="button"
      aria-label={`${territory.moduleName} territory`}
      data-testid="territory"
      data-module-name={territory.moduleName}
      data-status={territory.status}
      aria-current={focused ? 'true' : undefined}
      onClick={onClick}
      className={`absolute flex flex-col justify-between rounded-2xl p-4 text-left shadow-sm transition-colors ${meta.className}`}
      style={{ left: box.x, top: box.y, width: box.width, height: box.height }}
    >
      <div>
        <span className="rounded-full bg-ink-100 px-2 py-0.5 text-xs font-medium text-ink-700">
          {territory.techStackLabel}
        </span>
        <h3 className="mt-2 font-display text-lg text-ink-900">{territory.moduleName}</h3>
      </div>
      <p className="text-sm text-ink-500">{territory.statusSummary}</p>
    </button>
  )
}

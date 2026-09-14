/**
 * The Semantic Canvas's own id scheme for a comment target (ticket #134):
 * a territory, or a concept/file node scoped to its owning territory (a
 * concept/file name is only unique within its territory, not globally, so
 * every node id is namespaced by moduleName to avoid cross-territory
 * collisions). Opaque to the backend — it just stores/returns this string.
 */
export function territoryItemId(moduleName: string): string {
  return `territory:${moduleName}`
}

export function conceptItemId(moduleName: string, conceptName: string): string {
  return `concept:${moduleName}:${conceptName}`
}

export function fileItemId(moduleName: string, fileName: string): string {
  return `file:${moduleName}:${fileName}`
}

// A selected PR is addressable at /repositories/:owner/:repo/pulls/:number so
// a reviewer can copy the browser's URL and send it to a teammate running
// Athena locally — opening it re-selects the same PR against the receiving
// user's own GitHub session (see PullRequestSelectionController), no shared
// filesystem or backend state involved.

export interface SelectedPrRoute {
  repositoryFullName: string
  number: number
}

export function pullRequestPath(route: SelectedPrRoute): string {
  return `/repositories/${route.repositoryFullName}/pulls/${route.number}`
}

export function parsePullRequestPath(pathname: string): SelectedPrRoute | null {
  const match = pathname.match(/^\/repositories\/([^/]+\/[^/]+)\/pulls\/(\d+)$/)
  if (!match) return null
  return { repositoryFullName: match[1], number: Number(match[2]) }
}

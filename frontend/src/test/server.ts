import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

// Default handlers survive server.resetHandlers(): every screen that shows the coverage
// indicator (ticket #261) fetches this, and most tests don't care about it — by default the
// analysis represents every changed file, so the indicator stays hidden.
export const server = setupServer(
  http.get('/api/review/unrepresented-files', () =>
    HttpResponse.json({ changedFileCount: 0, representedFileCount: 0, files: [] }),
  ),
)

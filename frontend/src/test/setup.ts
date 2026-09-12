import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

// The backend (a separate Spring Boot process) is the one real external
// boundary this frontend test suite can't cross for real — MSW intercepts
// only that network call, everything else (React rendering, TanStack Query)
// runs for real.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

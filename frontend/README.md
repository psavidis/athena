# Athena web frontend

React + TypeScript + Vite + Tailwind CSS + TanStack Query, talking to the
Spring Boot backend (`com.athena.web`, see the repo root `README.md`) as
a plain JSON REST API — no server-side rendering.

## Run it

```bash
npm install
npm run dev
```

The dev server proxies `/api/*` to `http://localhost:8080` (see
`vite.config.ts`), so start the backend first (`mvn spring-boot:run` from
the repo root).

## Status

Covers the connect → pick repository → pick PR flow (ticket #73). No
animation/motion yet (Framer Motion) and no code/diff highlighting —
both explicitly deferred until a later ticket first has something to
animate or a diff to render.

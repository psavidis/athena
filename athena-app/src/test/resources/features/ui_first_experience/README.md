# UI-First Experience

A browser-based UI, backed by a Spring Boot application, as the primary
way to use Athena. A reviewer authenticates with a GitHub token, picks a
repository and PR, and works the whole review loop — Change Map,
drill-down, comments/private notes, review state, submission, optionally
AI analysis — from the browser. The Spring Boot layer is purely an
application/web layer over the existing domain library
(`com.athena.github`, `com.athena.semantic`, `com.athena.reviewui`,
`com.athena.reviewcontext`, `com.athena.ai`) — no new domain logic here,
just wiring those capabilities onto a JSON REST API and a React +
TypeScript frontend.

See Epic #71 for the full vision and scope boundary. Backend
(`src/test/resources/features/ui_first_experience/`) and frontend
(`frontend/src/test/resources/features/ui_first_experience/`) features
both live under this same capability name — one on the server side of
the API boundary, one on the rendering side.

## Backend features (`src/test/resources/features/ui_first_experience/`)

- `web_session_pr_selection.feature` — authenticating and selecting a repo/PR over HTTP
- `web_change_map.feature` — Change Map & PR Understanding View API
- `web_change_drilldown_and_annotations.feature` — Change drill-down, comments & private notes API
- `web_ai_analysis_and_findings.feature` — AI analysis trigger & findings review API
- `web_review_state_and_submission.feature` — review state, pre-submission summary & GitHub submission API
- `web_module_narratives.feature` — module-level "what changed and why" narratives API

## Frontend features (`frontend/src/test/resources/features/ui_first_experience/`)

- `change_map_frontend_rendering.feature` — Change Map & PR Understanding View rendering
- `change_drilldown_frontend_rendering.feature` — Change drill-down, comments & private notes rendering
- `ai_analysis_frontend_rendering.feature` — AI analysis trigger & findings review rendering
- `review_state_and_submission_frontend_rendering.feature` — review state, pre-submission summary & submission rendering

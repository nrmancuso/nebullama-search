# Issue #57: Frontend Search Demo

## Goal

Create the first frontend for `nebullama-search`: a Dockerized React app that runs behind the
Compose `local` profile, queries the live GraphQL API, renders search results in a table, and
shows row details plus interpretation metadata in a side drawer.

## Why

The backend is currently easiest to access through GraphiQL and `curl`. That is fine for API
development but weak for demos, result exploration, and fast iteration on search behavior.
The first frontend should make the system easier to inspect without turning into a large
application or introducing a heavier client architecture than the repo needs today.

## Scope

This work adds one new frontend module and the minimum infrastructure needed to run it
through `docker compose --profile local up -d --build`.

Included:

- `frontend/` Vite app with React and TypeScript
- frontend container and Compose service
- GraphQL integration against the existing backend API
- one search workspace screen
- query input and pragmatic structured filters
- results table
- row detail side drawer
- loading, empty, and error states
- pagination controls
- docs updates for local startup and usage

Excluded:

- ingest UI
- authentication or user accounts
- route-heavy app structure
- Apollo Client or code generation
- advanced visual polish beyond a strong, usable local demo

## Architecture

### Runtime Model

The frontend lives in `frontend/`, runs in Docker, and is enabled only through the Compose
`local` profile. It is exposed on `http://localhost:5173`.

Vite proxies `/graphql` to the Spring service container. This keeps browser traffic on one
origin, avoids CORS setup for the first version, and lets the React code use a relative API
path instead of environment-specific backend URLs.

### Application Shape

The UI is a single-screen search workspace with three visible regions:

- search controls across the top
- results table as the primary content
- right-side details drawer for the selected row

The drawer stays on the same screen rather than using a dedicated route. This keeps search
context intact, makes result-to-result inspection fast, and avoids adding routing complexity
before the app needs linkable result pages.

### Client Data Strategy

Use plain `fetch` with a small typed API module rather than Apollo Client.

Reasons:

- lower dependency and mental overhead
- enough for a single query-driven screen
- easier to test and debug in a small local demo
- preserves flexibility to adopt a richer GraphQL client later if the UI expands

## GraphQL Contract

The frontend should target the current schema exactly and should not invent API behavior.

Relevant operations:

- `search(input: SearchInput!): SearchResults!`
- `searchIndex(resourceType: ResourceType!, input: SearchInput!): SearchResults!`

The first version should primarily use `search` and rely on `filters.resourceTypes` for
cross-index and narrowed searches. `searchIndex` remains available for future index-specific
flows but is not required for the initial UI.

Relevant response fields:

- `total`
- `hits[].id`
- `hits[].resourceType`
- `hits[].score`
- `hits[].source`
- `interpretation.rewrittenQuery`
- `interpretation.extractedFilters`
- `interpretation.searchMode`

Current backend behavior to reflect honestly in the UI:

- `searchMode` is deterministic based on request shape
- `rewrittenQuery` currently echoes the input query
- `extractedFilters` currently comes back as an empty object

The frontend should display those fields as returned, without trying to reinterpret or hide
their current limitations.

## UI Design

### Search Controls

The search controls should support:

- free-text query
- multi-select `resourceTypes`
- optional structured filters that map to existing GraphQL fields:
  - `objectType`
  - `agency`
  - `status`
  - `wavelengthBand`
  - `journal`
  - `nationality`
  - `yearFrom`
  - `yearTo`
- page size selection if it can be added simply

The filters should be pragmatic rather than highly styled. A compact main bar plus an
expandable "more filters" section is enough. The goal is coverage of backend capability, not
faceted-search polish.

### Results Table

The results table should show at least:

- a primary label derived from `source`
- `resourceType`
- `score`
- a summary snippet derived from `source`

Label derivation should prefer common fields in this order when present:

- `name`
- `title`
- `mission_name`
- fallback to `id`

Summary derivation should prefer concise text fields such as:

- `description`
- `abstract`
- `notes`
- `biography`
- fallback to an empty value if none exist

The table should remain readable across mixed resource types and should not assume one common
document shape.

### Details Drawer

Clicking a table row opens a right-side drawer that shows:

- `id`
- `resourceType`
- `score`
- the raw `source` JSON
- the shared response interpretation block for the current query:
  - `searchMode`
  - `rewrittenQuery`
  - `extractedFilters`

The drawer is the main debugging surface for this version of the UI. It should make it easy
to compare what the table summarizes with what the backend actually returned.

### States

The UI must handle:

- initial idle state before the first search
- loading state during request execution
- empty state when a query returns zero hits
- error state when the backend request fails

These states should be explicit and readable. A failed request should not leave stale results
on screen without explanation.

## Component Boundaries

Keep the frontend intentionally small and decomposed into a few focused units:

- `SearchPage`
  - owns query, filters, pagination, selected row, and request lifecycle
- `searchApi`
  - sends GraphQL requests and returns typed results
- `SearchForm`
  - renders query and filters, emits normalized search input
- `ResultsTable`
  - renders hits, loading/empty states, and row selection
- `DetailsDrawer`
  - renders the selected row and interpretation metadata

This keeps data flow straightforward and avoids a premature global state solution.

## Docker and Compose

Add the frontend to `docker-compose.yml` behind the `local` profile rather than the default
stack.

Reasoning:

- the frontend is a local developer/demo surface and should not run in CI by default
- the backend stack should remain available without the frontend for automated workflows
- the repo should use the `local` profile consistently for local browser-facing tools and
  guides

The expected local flow after this ticket:

1. `docker compose --profile local up -d --build`
2. `./scripts/init.sh`
3. open `http://localhost:5173`

The following local-facing docs should also standardize on the `local` profile form:

- `README.md` quick start
- `docs/guides/local-dev-setup.md`
- `docs/guides/running-searches.md`

## Testing

Use lightweight frontend tests focused on behavior instead of a heavy end-to-end framework.

The test set should cover:

- initial page render
- successful search request and table rendering
- row click opens the details drawer
- empty results state
- backend failure state
- pagination interaction
- API helper request/response shaping

The implementation should prefer test tools that fit naturally with Vite and React, such as
Vitest and Testing Library.

## Documentation

Update docs to reflect the new local surface:

- `README.md`
- `docs/guides/local-dev-setup.md`
- `docs/guides/running-searches.md`

The docs should explain:

- the frontend is part of the local developer stack enabled through the `local` profile
- the frontend URL
- that GraphiQL still exists for raw API inspection
- that the frontend is a read-only search demo, not an ingest console

## Acceptance Criteria

- `docker compose --profile local up -d --build` starts the frontend
- the frontend is reachable on `http://localhost:5173`
- a user can execute a live search against the local GraphQL API
- results render in a table with a derived label, resource type, score, and summary
- clicking a row opens a side drawer with raw payload and interpretation metadata
- pagination works
- loading, empty, and error states are explicit
- docs describe how to launch and use the frontend locally with the `local` profile

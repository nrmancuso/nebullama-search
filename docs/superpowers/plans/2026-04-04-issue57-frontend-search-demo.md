# Frontend Search Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Dockerized React search demo under `frontend/` that runs behind the Compose `local` profile, queries the live GraphQL API, renders results in a table, and shows row details plus interpretation metadata in a side drawer.

**Architecture:** Build a small Vite + React + TypeScript app with a thin `fetch`-based GraphQL client, one main page, and focused presentational components. Keep the frontend opt-in via the Compose `local` profile, verify behavior with Vitest + Testing Library, and update quickstart and local guides so browser-oriented workflows consistently use `docker compose --profile local ...`.

**Tech Stack:** Vite, React, TypeScript, Vitest, Testing Library, Docker Compose, Markdown

---

## Tasks

### Task 1: Scaffold the frontend module and lock the toolchain

**Files:**

- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/.gitignore`

- [ ] **Step 1: Write the failing frontend smoke test**

Create `frontend/src/App.test.tsx` with a test that expects the app shell to render a visible heading and a search form region:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the search workspace shell', () => {
    render(<App />);

    expect(
        screen.getByRole('heading', { name: /nebullama search explorer/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole('search')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the smoke test to verify it fails**

Run:

```bash
cd frontend && npm test -- --run App.test.tsx
```

Expected: fail because `frontend/` and the test runner do not exist yet.

- [ ] **Step 3: Create the Vite/React project files with test wiring**

Create a minimal Vite app configured for React and Vitest. `package.json` should include scripts for:

```json
{
  "dev": "vite --host 0.0.0.0 --port 5173",
  "build": "tsc -b && vite build",
  "preview": "vite preview --host 0.0.0.0 --port 4173",
  "test": "vitest"
}
```

Dependencies should include:

```json
{
  "react": "^18.3.1",
  "react-dom": "^18.3.1"
}
```

Dev dependencies should include:

```json
{
  "@testing-library/jest-dom": "^6.6.3",
  "@testing-library/react": "^16.3.0",
  "@types/react": "^18.3.12",
  "@types/react-dom": "^18.3.1",
  "@vitejs/plugin-react": "^4.3.4",
  "jsdom": "^25.0.1",
  "typescript": "^5.6.3",
  "vite": "^5.4.10",
  "vitest": "^2.1.4"
}
```

`vite.config.ts` should:

- register the React plugin
- configure Vitest with `environment: 'jsdom'`
- load `src/test/setup.ts`
- proxy `/graphql` to `http://service:8080`

`src/test/setup.ts` should import:

```ts
import '@testing-library/jest-dom/vitest';
```

- [ ] **Step 4: Implement the minimal app shell**

Create `frontend/src/App.tsx` and `frontend/src/main.tsx` so the app renders:

- heading: `Nebullama Search Explorer`
- one paragraph explaining it is a local search demo
- a `<main>` containing a `<section role="search">`

- [ ] **Step 5: Run the smoke test to verify it passes**

Run:

```bash
cd frontend && npm test -- --run App.test.tsx
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend
git commit -m "Issue #57: scaffold frontend module"
```

---

### Task 2: Add a typed GraphQL client and request-state model

**Files:**

- Create: `frontend/src/api/searchApi.ts`
- Create: `frontend/src/api/searchApi.test.ts`
- Create: `frontend/src/types/search.ts`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Write the failing API test**

Create `frontend/src/api/searchApi.test.ts` with a test that verifies the GraphQL request shape sent to `/graphql`:

```tsx
import { describe, expect, it, vi } from 'vitest';
import { runSearch } from './searchApi';

describe('runSearch', () => {
  it('posts the search query and variables to /graphql', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          search: {
            total: 0,
            hits: [],
            interpretation: {
              rewrittenQuery: '',
              extractedFilters: {},
              searchMode: 'SEMANTIC',
            },
          },
        },
      }),
    });

    vi.stubGlobal('fetch', fetchMock);

    await runSearch({
      query: 'Crab Nebula',
      filters: { resourceTypes: ['CELESTIAL_OBJECTS'] },
      pagination: { from: 0, size: 10 },
    });

    expect(fetchMock).toHaveBeenCalledWith(
      '/graphql',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      }),
    );
  });
});
```

- [ ] **Step 2: Run the API test to verify it fails**

Run:

```bash
cd frontend && npm test -- --run src/api/searchApi.test.ts
```

Expected: fail because `runSearch` does not exist.

- [ ] **Step 3: Implement the minimal request types and API helper**

Create `frontend/src/types/search.ts` with TypeScript types for:

- `ResourceType`
- `SearchMode`
- `SearchFilters`
- `Pagination`
- `SearchRequestInput`
- `SearchHit`
- `SearchInterpretation`
- `SearchResponse`

Create `frontend/src/api/searchApi.ts` with:

- a string constant holding the GraphQL search operation
- `runSearch(input: SearchRequestInput): Promise<SearchResponse>`
- fetch to `/graphql`
- JSON parsing for `data.search`
- error handling that throws when `response.ok` is false or GraphQL `errors` is present

- [ ] **Step 4: Wire the shell to hold request state**

Update `frontend/src/App.tsx` to introduce minimal state for:

- current query string
- loading flag
- error message
- last response

The UI does not need the full form yet. It only needs enough state shape to support the next tasks cleanly.

- [ ] **Step 5: Run the API test to verify it passes**

Run:

```bash
cd frontend && npm test -- --run src/api/searchApi.test.ts
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api frontend/src/types frontend/src/App.tsx
git commit -m "Issue #57: add frontend GraphQL client"
```

---

### Task 3: Build the search form and successful result flow

**Files:**

- Create: `frontend/src/components/SearchForm.tsx`
- Create: `frontend/src/components/ResultsTable.tsx`
- Create: `frontend/src/components/SearchPage.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/types/search.ts`

- [ ] **Step 1: Write the failing search flow test**

Create `frontend/src/components/SearchPage.test.tsx` with a test that submits a query and renders a result row:

```tsx
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from '../App';

describe('SearchPage', () => {
  it('submits a query and renders the results table', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          data: {
            search: {
              total: 1,
              hits: [
                {
                  id: 'co-1',
                  resourceType: 'CELESTIAL_OBJECTS',
                  score: 0.98,
                  source: {
                    name: 'Crab Nebula',
                    description: 'A supernova remnant in Taurus',
                  },
                },
              ],
              interpretation: {
                rewrittenQuery: 'Crab Nebula',
                extractedFilters: {},
                searchMode: 'SEMANTIC',
              },
            },
          },
        }),
      }),
    );

    render(<App />);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Crab Nebula' },
    });
    fireEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => {
      expect(screen.getByRole('table')).toBeInTheDocument();
    });

    expect(screen.getByText('Crab Nebula')).toBeInTheDocument();
    expect(screen.getByText('CELESTIAL_OBJECTS')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the search flow test to verify it fails**

Run:

```bash
cd frontend && npm test -- --run src/components/SearchPage.test.tsx
```

Expected: fail because the form and table do not exist yet.

- [ ] **Step 3: Implement the search form and result table**

Create `SearchForm.tsx` with:

- text input labeled `Search Query`
- resource type multiselect using checkboxes
- collapsible optional filters section for the supported GraphQL filter fields
- submit button labeled `Search`

Create `ResultsTable.tsx` with columns:

- `Name`
- `Resource Type`
- `Score`
- `Summary`

Derive the label from `source` in this order:

1. `name`
2. `title`
3. `mission_name`
4. fallback to `id`

Derive the summary from:

1. `description`
2. `abstract`
3. `notes`
4. `biography`
5. fallback to an empty string

Update `App.tsx` so submit:

- normalizes form state into `SearchRequestInput`
- calls `runSearch`
- shows loading text while pending
- renders the table when hits are present

- [ ] **Step 4: Run the search flow test to verify it passes**

Run:

```bash
cd frontend && npm test -- --run src/components/SearchPage.test.tsx
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components frontend/src/App.tsx frontend/src/types/search.ts
git commit -m "Issue #57: add frontend search form and results table"
```

---

### Task 4: Add the details drawer, empty state, error state, and pagination

**Files:**

- Create: `frontend/src/components/DetailsDrawer.tsx`
- Modify: `frontend/src/components/SearchPage.test.tsx`
- Modify: `frontend/src/components/ResultsTable.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Extend the failing UI test for drawer and states**

Add three tests to `frontend/src/components/SearchPage.test.tsx`:

- clicking a row opens a details drawer showing raw `source` and `interpretation.searchMode`
- zero hits shows a no-results message
- failed backend request shows an error message

Example drawer assertion:

```tsx
expect(screen.getByRole('complementary')).toBeInTheDocument();
expect(screen.getByText(/SEMANTIC/i)).toBeInTheDocument();
expect(screen.getByText(/Crab Nebula/)).toBeInTheDocument();
```

- [ ] **Step 2: Add a failing pagination test**

Add a test asserting that clicking `Next Page` triggers a second request with `from: 10` when page size is 10.

- [ ] **Step 3: Run the UI tests to verify they fail**

Run:

```bash
cd frontend && npm test -- --run src/components/SearchPage.test.tsx
```

Expected: fail because the drawer, empty/error states, and pagination controls are not implemented yet.

- [ ] **Step 4: Implement the drawer and state handling**

Create `DetailsDrawer.tsx` with:

- heading derived from the selected result label
- metadata rows for `id`, `resourceType`, `score`
- interpretation section showing `searchMode`, `rewrittenQuery`, `extractedFilters`
- pretty-printed JSON block for `source`
- close button

Update `App.tsx` and `ResultsTable.tsx` so:

- clicking a row selects the hit and opens the drawer
- requests that return zero hits show an explicit empty state
- failed requests show an explicit error banner
- pagination controls show current range and call `runSearch` with updated `from`

- [ ] **Step 5: Run the UI tests to verify they pass**

Run:

```bash
cd frontend && npm test -- --run src/components/SearchPage.test.tsx
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components frontend/src/App.tsx
git commit -m "Issue #57: add frontend details drawer and pagination"
```

---

### Task 5: Add frontend styling and container runtime support

**Files:**

- Create: `frontend/src/styles.css`
- Create: `frontend/Dockerfile`
- Modify: `frontend/src/main.tsx`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Write the failing runtime verification**

Run:

```bash
docker compose --profile local config
```

Expected: fail to show a `frontend` service because it does not exist yet.

- [ ] **Step 2: Add the failing container build verification**

Run:

```bash
docker compose --profile local build frontend
```

Expected: fail because there is no frontend Dockerfile or service definition yet.

- [ ] **Step 3: Implement the frontend Docker and Compose wiring**

Create `frontend/Dockerfile` using a Node 20 image that:

- installs dependencies with `npm ci`
- copies the frontend sources
- runs `npm run dev -- --host 0.0.0.0 --port 5173`

Update `docker-compose.yml` to add:

- `frontend` service
- `profiles: ["local"]`
- `ports: ["5173:5173"]`
- `depends_on` the backend `service`
- build context `./frontend`

- [ ] **Step 4: Add a deliberate visual layer**

Create `frontend/src/styles.css` and import it from `main.tsx`. The UI should:

- use CSS variables for color choices
- avoid a flat default-white layout
- create a distinct demo feel with a textured/gradient background
- keep the table and drawer readable on desktop and mobile

The styling should stay modest and purposeful, not generic component-library defaults.

- [ ] **Step 5: Run Compose verification**

Run:

```bash
docker compose --profile local config
docker compose --profile local build frontend
```

Expected:

- config output includes `frontend`
- frontend image builds successfully

- [ ] **Step 6: Commit**

```bash
git add frontend docker-compose.yml
git commit -m "Issue #57: add frontend container and compose wiring"
```

---

### Task 6: Update docs and run final verification

**Files:**

- Modify: `README.md`
- Modify: `docs/guides/local-dev-setup.md`
- Modify: `docs/guides/running-searches.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Update local startup and usage docs**

Update the docs so local browser-facing workflows use the `local` profile form:

- `README.md` quick start should use `docker compose --profile local up -d --build`
- `docs/guides/local-dev-setup.md` should use the same command for local startup
- `docs/guides/running-searches.md` should note both GraphiQL and the frontend UI path
- `AGENTS.md` should retain the instruction that issue titles never use prefixes

- [ ] **Step 2: Run frontend tests**

Run:

```bash
cd frontend && npm test -- --run
```

Expected: all frontend tests pass

- [ ] **Step 3: Run markdown lint**

Run:

```bash
npx markdownlint-cli2 "README.md" "AGENTS.md" "docs/guides/*.md" "docs/superpowers/specs/2026-04-04-issue57-frontend-search-demo-design.md"
```

Expected: `Summary: 0 error(s)`

- [ ] **Step 4: Run live stack verification**

Run:

```bash
docker compose --profile local up -d --build
./scripts/init.sh
curl -sf http://localhost:8080/actuator/health
curl -I -s http://localhost:5173
```

Expected:

- Compose stack starts successfully with the local profile
- backend health endpoint returns success
- frontend returns `HTTP/1.1 200 OK`

- [ ] **Step 5: Commit**

```bash
git add README.md docs/guides/local-dev-setup.md docs/guides/running-searches.md AGENTS.md docs/superpowers/specs/2026-04-04-issue57-frontend-search-demo-design.md docs/superpowers/plans/2026-04-04-issue57-frontend-search-demo.md
git commit -m "Issue #57: document frontend local workflow"
```

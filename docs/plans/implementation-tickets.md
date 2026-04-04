# NebullamaSearch — Implementation Plan & GitHub Tickets

## Context

NebullamaSearch is a learning project to understand vector search, LLMs, and hybrid search patterns, with the goal of eventually applying these patterns at work. The project is a Spring Boot 3.x (Java 21) service exposing a GraphQL search API and REST ingest API over five astronomy-themed OpenSearch indexes. Hybrid search combines BM25 keyword matching with k-NN vector similarity using Ollama-generated embeddings, with an LLM intent extraction layer for natural language query parsing.

This plan breaks the project into medium-sized GitHub tickets with clear deliverables, ordered by dependency. Frontend is deferred to a later phase. Docs are written incrementally as components are built.

---

## Key Technical Decisions

- **Hybrid search**: Separate BM25 + k-NN queries combined in Java with min-max normalization (not OpenSearch search pipelines — intentional, to learn the internals)
- **Intent model**: `mistral:7b` (best JSON reliability for structured output), configurable to swap via `application.yml`
- **Embedding model**: `nomic-embed-text` (768 dimensions)
- **OpenSearch version**: 2.x with k-NN plugin (not search pipelines)
- **Testing**: Unit tests with mocks in main project; real integration tests in `integration-tests/` Gradle subproject
- **Seed data**: ~200 real documents (40 per index) from public APIs; no LLM-generated content
- **Frontend**: Deferred to future phase
- **Docs**: Written incrementally per ticket, stored in Obsidian vault at `docs/`

---

## Ticket Dependency Graph

```text
T12 (no deps — can be done anytime)
T13 (no deps — can be done anytime)
T14 (no deps — can be done anytime)

T1 ──→ T2 ──→ T5 ──→ T6 ──→ T8 ──→ T10
 │             ↑       ↑      ↑       ↑
 ├──→ T3 ─────┘       │      │       │
 │                     │      │       │
 └──→ T4 ─────────────┴──────┘       │
                                      │
      T9 ─────────────────────────────┘

      T11 (starts after T5, grows alongside each ticket)
      T15 (after T5 — depends on ingest API being live)
      T16 (no code deps — can be written anytime)
```

## Implementation Phases

| Phase | Tickets | What you'll learn |
| --- | --- | --- |
| 0. Identity & Docs Shell | T12, T13, T14 (parallel, no deps) | Obsidian vault, project identity |
| 1. Foundation | T1, T2, T3 (T2+T3 parallel after T1) | Docker Compose, OpenSearch k-NN mappings, public API data fetching |
| 2. Embedding + Ingest | T4, T5 (T5 after T4) | Ollama embedding API, vector generation, OpenSearch writes |
| 2b. Dev Tooling | T15 (after T5) | Shell scripting, curl bulk operations |
| 3. Search | T6, T7, T8 (T6+T7 parallel, T8 after both) | BM25 internals, k-NN queries, score normalization |
| 4. Intelligence | T9, T10 (T9 can overlap T6–T8; T10 after all) | LLM structured output, GraphQL wiring, full pipeline |
| 4b. Deployment Docs | T16 (can be written anytime after T10 concepts are clear) | AWS Bedrock, OSS, ECS Fargate |
| 5. Validation | T11 | Integration testing patterns |

---

## Tickets

---

### T1: Project Scaffolding & Docker Compose

**Goal:** Get the foundational project structure, build config, and local infrastructure running end-to-end.

**Deliverables:**

### Docker Compose (`docker-compose.yml`)

- OpenSearch 2.x: single node, security plugin disabled (`DISABLE_SECURITY_PLUGIN=true`), heap size env vars
- OpenSearch Dashboards 2.x: points at OpenSearch, port 5601
- Ollama: mount named volume for model persistence, port 11434
- Named volume `opensearch-data` mounted to `/usr/share/opensearch/data` for index persistence across restarts
- All ports exposed to localhost: 9200 (OpenSearch), 5601 (Dashboards), 11434 (Ollama)

### Init script (`scripts/init.sh`)

- Waits for Ollama container to be healthy
- Pulls `nomic-embed-text` (embedding model)
- Pulls `mistral:7b` (intent extraction model)
- Idempotent (safe to re-run)

### Spring Boot project (`service/`)

- Gradle Kotlin DSL, Gradle wrapper 8.x
- `build.gradle.kts` dependencies:
  - `spring-boot-starter-graphql`
  - `spring-boot-starter-web`
  - `spring-boot-starter-actuator`
  - `opensearch-java:2.x`
  - `jackson-databind`
  - `spring-boot-starter-test` (test)
  - `spring-webflux` (test — required by Spring GraphQL test client)
- Java 21 toolchain
- `application.yml` with all config sections:

  ```yaml
  spring:
    threads:
      virtual:
        enabled: true

  opensearch:
    host: localhost
    port: 9200
    scheme: http

  ollama:
    base-url: http://localhost:11434
    embedding-model: nomic-embed-text
    intent-model: mistral

  search:
    hybrid-weight:
      bm25: 0.4
      knn: 0.6
    knn-k: 10
    intent-extraction:
      enabled: true
      timeout-ms: 3000
  ```

- Main application class `NebullamaSearchApplication.java`
- Placeholder empty packages: `config/`, `ingest/`, `search/`, `domain/`, `util/`

### Repo root

- `.gitignore` covering: Java/Gradle build outputs, `.idea/`, `*.class`, `data/` seed JSON files (large/generated), Python `venv/`, `__pycache__`, `.env`

**Acceptance:**

- `docker-compose up -d` starts OpenSearch, Dashboards, and Ollama
- `scripts/init.sh` pulls both models without error
- `./gradlew bootRun` starts Spring Boot on port 8080
- `GET /actuator/health` returns `{"status":"UP"}`
- OpenSearch Dashboards accessible at `localhost:5601`

**Docs:** `docs/guides/local-dev-setup.md`

- Prerequisites: Java 21, Docker, Docker Compose, Gradle (or use wrapper)
- Step by step: clone → `docker-compose up -d` → `scripts/init.sh` → `./gradlew bootRun`
- Verification: health check, Dashboards URL
- Troubleshooting: Ollama model not found, OpenSearch heap OOM, port conflicts

---

### T2: OpenSearch Index Configuration & Mappings

**Goal:** Define and auto-create the five astronomy indexes with correct field mappings including k-NN vector fields.

**Deliverables:**

### Index mappings (`src/main/resources/opensearch/`)

- One JSON mapping file per index: `celestial_objects.json`, `missions.json`, `observations.json`, `astronomers.json`, `publications.json`
- Every index includes:
  - `resource_type`: `keyword` — shared field for cross-index result unification
  - `embedding`: `knn_vector`, dimension 768, `space_type: cosinesimil`
  - Index settings: `"knn": true`
- Per-index fields as specified in the plan:
  - `celestial_objects`: id (keyword), name (text), designations (text[]), object_type (keyword), constellation (keyword), distance_ly (double), description (text), discovered_by (keyword), discovery_year (integer)
  - `missions`: id (keyword), name (text), agency (keyword), mission_type (keyword), launch_year (integer), status (keyword), targets (keyword[]), description (text)
  - `observations`: id (keyword), target_name (text), instrument (keyword), observatory (keyword), observation_date (date), wavelength_band (keyword), notes (text)
  - `astronomers`: id (keyword), name (text), birth_year (integer), death_year (integer), nationality (keyword), known_for (text), associated_objects (keyword[]), associated_missions (keyword[]), biography (text)
  - `publications`: id (keyword), title (text), authors (keyword[]), year (integer), journal (keyword), abstract (text), topics (keyword[]), doi (keyword)

### Java domain (`domain/` package)

- One POJO per resource type matching the field definitions above
- `ResourceType` enum: `CELESTIAL_OBJECTS`, `MISSIONS`, `OBSERVATIONS`, `ASTRONOMERS`, `PUBLICATIONS` — with a method returning the index name string

### OpenSearch client config (`config/OpenSearchConfig.java`)

- `@Configuration` bean creating `OpenSearchClient` from `opensearch.host`, `opensearch.port`, `opensearch.scheme` config
- Bind config via `@ConfigurationProperties`

### Index initializer (`config/IndexInitializer.java`)

- `ApplicationRunner` that runs at startup
- For each of the five indexes: checks if it exists (`indices.exists`), creates it with mappings if not
- Logs creation vs. already-exists result
- Idempotent — safe to restart

**Acceptance:**

- Boot the app with OpenSearch running → all five indexes created with correct mappings
- Verify in OpenSearch Dashboards (Dev Tools): `GET /celestial_objects/_mapping` shows `knn_vector` field with dimension 768
- Restarting the app does not fail or recreate indexes

**Docs:** Update `docs/guides/local-dev-setup.md` with a verification section: how to confirm indexes were created in Dashboards

---

### T3: Seed Data Fetching Script (Python)

**Goal:** Automated Python script that fetches ~200 real astronomy documents from public APIs and writes per-index JSON seed files.

**Deliverables:**

### Script (`scripts/fetch_seed_data.py`) + `scripts/requirements.txt`

Anchor subjects (cross-linked across all indexes):
> Crab Nebula, Andromeda Galaxy, Cygnus X-1, Hubble Space Telescope, James Webb Space Telescope, Voyager 1, Cassini, Chandra X-ray Observatory, Jocelyn Bell Burnell, Carl Sagan, Vera Rubin, Edwin Hubble

### Per-index fetch logic

`celestial_objects` (40 docs) — SIMBAD TAP/ADQL:

- Query SIMBAD via TAP endpoint for objects related to anchor subjects + fill to 40 with varied object types (stars, nebulae, galaxies, pulsars, black holes)
- Supplement `description` field with Wikipedia API summary (first paragraph of article for the object name)
- Fields: name, designations, object_type, constellation, distance_ly, description, discovered_by, discovery_year

`missions` (40 docs) — NASA API + Wikipedia API:

- NASA EPIC/missions data for anchor missions (HST, JWST, Voyager, Cassini, Chandra, New Horizons, etc.)
- Wikipedia API for description, agency, launch year, status, targets
- Fill to 40 with other notable missions

`observations` (40 docs) — MAST Portal API:

- Query MAST for real HST and JWST observation records targeting anchor celestial objects
- Fields: target_name, instrument (e.g. "HST/ACS", "JWST/NIRCam"), observatory, observation_date, wavelength_band, notes (proposal abstract)

`astronomers` (40 docs) — Wikipedia API:

- Anchor astronomers + others with rich Wikipedia articles
- Parse infobox for birth_year, death_year, nationality
- Biography = first 3 paragraphs of article body
- known_for, associated_objects, associated_missions extracted from article text

`publications` (40 docs) — NASA ADS API (token via `ADS_TOKEN` env var):

- Query ADS for papers citing or about anchor subjects
- Fields: title, authors, year, journal, abstract, topics (keywords), doi

### Output

- `data/seed_celestial_objects.json` — JSON array, 40 docs
- `data/seed_missions.json` — JSON array, 40 docs
- `data/seed_observations.json` — JSON array, 40 docs
- `data/seed_astronomers.json` — JSON array, 40 docs
- `data/seed_publications.json` — JSON array, 40 docs
- Each document matches ingest API schema; omit `embedding` and `id` (generated server-side)
- Script is idempotent (re-run overwrites output files)
- Prints progress per index; logs skipped/failed records

**Acceptance:**

- `pip install -r scripts/requirements.txt && ADS_TOKEN=<token> python scripts/fetch_seed_data.py` produces all 5 files
- Each file contains ~40 real documents
- Cross-references are present (e.g. "Crab Nebula" appears in celestial_objects AND as `target_name` in observations AND in an ADS publication abstract AND as an `associated_objects` entry for an astronomer)

**Docs:** `docs/guides/data-ingestion.md`

- How to get an ADS API token (free, ui.adsabs.harvard.edu)
- How to run the script
- How to bulk ingest each seed file via curl
- How to verify documents landed in OpenSearch Dashboards

---

### T4: Ollama Embedding Service

**Goal:** Java service that calls the Ollama API to generate vector embeddings from text strings.

**Deliverables:**

**`OllamaProperties.java`** (`config/` package) — `@ConfigurationProperties(prefix = "ollama")`:

- `baseUrl`, `embeddingModel`, `intentModel`
- Connect timeout and read timeout (with defaults)

**`OllamaEmbeddingService.java`** (`ingest/` package — used by both ingest and search):

- Uses Spring `RestClient` (not WebClient — blocking is fine with virtual threads)
- `embed(String text): float[]`
  - `POST {baseUrl}/api/embeddings` with body `{ "model": "<embeddingModel>", "prompt": "<text>" }`
  - Parses `embedding` float array from response JSON
  - Throws a typed `EmbeddingException` on HTTP error or parse failure
- `RestClient` configured with connect/read timeouts from properties

**Unit tests:**

- Mock `RestClient` exchange — verify correct URL, request body shape
- Verify float[] is parsed correctly from a fixture response
- Verify `EmbeddingException` thrown on error response

**Acceptance:**

- With Ollama running and `nomic-embed-text` pulled: `embed("The Crab Nebula is a supernova remnant")` returns a `float[]` of length 768
- Unit tests pass with no Ollama running (mocked)

---

### T5: REST Ingest API

**Goal:** REST endpoints that accept raw documents, generate embeddings, and write them to OpenSearch.

**Deliverables:**

**`IngestController.java`** (`ingest/` package):

- `POST /api/v1/ingest/{resourceType}` — single document ingest; returns `201 Created` with `{"id": "<uuid>"}`
- `POST /api/v1/ingest/{resourceType}/bulk` — bulk ingest; returns `207 Multi-Status` with array of `{"id", "success", "error"}` per document
- Validates `resourceType` path param against `ResourceType` enum → 400 if invalid

**`IngestService.java`** (`ingest/` package):

- Primary text field mapping (used to select what to embed):
  - `celestial_objects` → `description`
  - `missions` → `description`
  - `observations` → `notes`
  - `astronomers` → `biography`
  - `publications` → `abstract`
- For each document:
  1. Generate UUID `id` if not present
  2. Set `resource_type` field to the index name
  3. Call `OllamaEmbeddingService.embed(primaryTextField)` to get `float[]`
  4. Add `embedding` field to document
  5. Index document into OpenSearch via `OpenSearchClient`
- Bulk: process documents in parallel using virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`); collect results; return all
- OpenSearch write failure for one doc in bulk does not abort the rest

**Unit tests:**

- Mock `OllamaEmbeddingService` and `OpenSearchClient`
- Single ingest: verify embedding called with correct text, document written with `id` and `resource_type`
- Bulk ingest: verify parallel execution, 207 response shape, partial failure handling
- Invalid resource type: verify 400 response

**Acceptance:**

- `POST /api/v1/ingest/celestial_objects` with Crab Nebula JSON → `201 {"id": "<uuid>"}`
- `POST /api/v1/ingest/celestial_objects/bulk` with seed file array → `207` with all successes
- Document visible in OpenSearch Dashboards with `embedding` array populated (length 768)
- All 5 seed files can be ingested: `curl -X POST .../bulk -d @data/seed_<type>.json`

**Docs:** `docs/api-reference/ingest-rest-api.md`

- Endpoint table: method, path, request body, response
- Full curl examples for single and bulk ingest per resource type
- Field reference per resource type (required vs optional)
- Error response shapes

---

### T6: BM25 Keyword Search

**Goal:** Text-based search across one or more indexes using OpenSearch's BM25 `multi_match` query.

**Deliverables:**

**`SearchService.java`** (`search/` package):

- `searchBM25(SearchRequest request): SearchResponse`
- Query construction:
  - `bool` query wrapping `multi_match` (for text relevance) + `filter` clauses (for structured filters)
  - `multi_match` targets text fields per index type: `name`, `description`/`notes`/`biography`/`abstract`, plus `known_for`, `title`, `target_name` as appropriate
  - Filter clauses for: `objectType` (`object_type`), `agency`, `status`, `wavelengthBand` (`wavelength_band`), `journal`, `nationality`, `yearFrom`/`yearTo` (range on year/launch_year/discovery_year)
- Index targeting: if `resourceTypes` is set, query only those indexes (comma-separated index names); otherwise query all five
- Uses OpenSearch `_msearch` for multi-index queries
- Pagination: `from` + `size` on the query
- Maps OpenSearch hits to `SearchHit` records: `id`, `resourceType` (from `_index`), `score`, `source` (raw `_source` as `Map<String, Object>`)

**DTOs** (`search/` package):

- `SearchRequest`: query (String), resourceTypes (`List<ResourceType>`), filters (SearchFilters), pagination (Pagination)
- `SearchFilters`: objectType, agency, status, wavelengthBand, journal, nationality, yearFrom, yearTo
- `Pagination`: from (default 0), size (default 10)
- `SearchResponse`: total (long), hits (`List<SearchHit>`)
- `SearchHit`: id (String), resourceType (ResourceType), score (float), source (Map<String, Object>)

**Unit tests:**

- Mock `OpenSearchClient`
- Verify `multi_match` query structure built correctly
- Verify filter clauses appended when filters provided
- Verify index targeting (all vs. subset)
- Verify hit mapping from mock OpenSearch response

**Acceptance:**

- With seeded data, BM25 search for "Crab Nebula" returns hits from multiple indexes
- Filtering `resourceTypes: [MISSIONS]` returns only mission documents
- Filter `agency: "NASA"` narrows correctly
- `yearFrom`/`yearTo` range filter works on numeric year fields

---

### T7: k-NN Vector Search

**Goal:** Semantic search using cosine similarity on the `embedding` field via OpenSearch's k-NN plugin.

**Deliverables:**

**Add to `SearchService.java`:**

- `searchKNN(SearchRequest request): SearchResponse`
- Embed the query string: call `OllamaEmbeddingService.embed(request.query())`
- Build k-NN query:

  ```json
  {
    "knn": {
      "embedding": {
        "vector": [...],
        "k": 10
      }
    }
  }
  ```

- `k` from `search.knn-k` config (default 10)
- Wrap in `bool` query with filter clauses (same filter logic as BM25)
- Same index targeting and result mapping as T6

**Unit tests:**

- Mock `OllamaEmbeddingService` and `OpenSearchClient`
- Verify embedding called with query string
- Verify k-NN query structure (vector populated, k set correctly)
- Verify filter clauses applied

**Acceptance:**

- k-NN search for "exploding star remnants" returns supernova/nebula results even if those exact words don't appear in indexed documents
- Results demonstrate semantic understanding (e.g., query "ancient astronomers who mapped the sky" returns relevant astronomer records)
- Score represents cosine similarity

---

### T8: Hybrid Search with Score Combination

**Goal:** Combine BM25 and k-NN results into a single ranked list using configurable weighted scoring.

**Deliverables:**

**Add to `SearchService.java`:**

- `searchHybrid(SearchRequest request): SearchResponse`
- Execute BM25 and k-NN queries in parallel (submit both to virtual thread executor, join results)
- **Score normalization** (min-max, applied independently to each result set):

  ```text
  normalizedScore = (score - min) / (max - min)
  ```

  Edge case: if all scores equal (max == min), set normalizedScore = 1.0

- **Score combination:**

  ```text
  finalScore = (bm25Weight * normalizedBM25Score) + (knnWeight * normalizedKNNScore)
  ```

  Documents only in BM25 results: `knnScore = 0`; only in k-NN: `bm25Score = 0`

- Weights from config: `search.hybrid-weight.bm25` (0.4), `search.hybrid-weight.knn` (0.6)
- Deduplicate by document `id` — same doc in both result sets gets one entry with combined score
- Re-sort descending by `finalScore`
- Apply pagination (from/size) after merge and sort
- `searchHybrid` becomes the default method used by the search pipeline

**Unit tests:**

- Verify min-max normalization math with known inputs
- Verify deduplication: same id in both sets → one result with combined score
- Verify doc only in BM25 → knn component = 0
- Verify sort order correct after combination
- Verify pagination applied after merge (not before)

**Acceptance:**

- Hybrid search for "Crab Nebula" returns results ranked by combined score
- A document matching both "Crab Nebula" as text AND semantically similar content ranks above one matching only keyword or only semantic
- No duplicates in results
- Tweaking weights in `application.yml` visibly changes result ranking

**Docs:** `docs/concepts/hybrid-search.md`

- What BM25 is: term frequency, inverse document frequency, length normalization
- What k-NN vector search is: approximate nearest neighbors, HNSW, cosine similarity
- Why hybrid beats either alone: exact catalog designations (BM25 wins) vs. "tell me about exploding stars" (k-NN wins)
- How scores are combined: the normalization and weighting math
- How to tune `hybrid-weight` config values

---

### T9: LLM Intent Extraction

**Goal:** Parse natural language queries into structured filters and a cleaned query string using Ollama's chat API.

**Deliverables:**

**`OllamaChatService.java`** (`search/` package):

- Low-level service for Ollama chat API
- `chat(String systemPrompt, String userMessage, int timeoutMs): String`
  - `POST {baseUrl}/api/chat` with `{ "model": "<intentModel>", "messages": [...], "stream": false }`
  - Returns the assistant's response content string
  - Throws `OllamaChatTimeoutException` on timeout, `OllamaChatException` on other failures

**`IntentExtractionService.java`** (`search/` package):

- `extract(String rawQuery): QueryInterpretation`
- System prompt instructs the model to return **only** a JSON object with no preamble:

  ```text
  You are a search query parser for an astronomy database. Given a user's search query,
  respond with ONLY a valid JSON object (no explanation, no markdown) with these fields:
  - cleanedQuery: string — the core search terms, stripped of meta-instructions
  - resourceTypeHints: string[] — zero or more of: celestial_objects, missions, observations, astronomers, publications
  - filters: object — any of: objectType, agency, status, wavelengthBand, journal, nationality, yearFrom (int), yearTo (int)
  - searchMode: string — one of: keyword, semantic, hybrid

  ```

- Calls `OllamaChatService.chat(systemPrompt, rawQuery, timeoutMs)`
- Parses the JSON response into `QueryInterpretation`
- **Fallback behavior** (returns a default `QueryInterpretation` with `cleanedQuery = rawQuery`, empty filters, `searchMode = HYBRID`):
  - LLM timeout (`OllamaChatTimeoutException`)
  - Unparseable JSON response
  - Intent extraction disabled via `search.intent-extraction.enabled: false`
- Logs the raw LLM response at DEBUG level for observability

**`QueryInterpretation.java`** DTO:

- `rewrittenQuery` (String) — the `cleanedQuery` from LLM
- `extractedFilters` (Map<String, Object>) — raw filter map from LLM
- `searchMode` (SearchMode enum) — KEYWORD, SEMANTIC, or HYBRID

**Unit tests:**

- Mock `OllamaChatService`
- Happy path: valid JSON response → correct `QueryInterpretation`
- Timeout → fallback result (raw query, HYBRID mode, empty filters)
- Malformed JSON response → fallback result
- `enabled: false` → fallback result without calling chat service
- Verify system prompt contains JSON-only instruction

**Acceptance:**

- Query "NASA missions to Jupiter launched after 2000" → `agency: NASA`, `yearFrom: 2000`, `resourceTypeHints: ["missions"]`, cleaned query about Jupiter
- Query "tell me about pulsars" → minimal/empty filters, `searchMode: hybrid`
- Timeout or bad JSON → graceful fallback, no exception thrown
- `search.intent-extraction.enabled: false` → service returns fallback without calling Ollama

**Docs:** `docs/concepts/intent-extraction.md`

- What intent extraction does: raw query → structured filters + cleaned query
- The system prompt contract: why JSON-only matters, example input/output pairs
- Fallback behavior and why it matters (Ollama might be slow or unavailable)
- How extracted filters map to OpenSearch filter clauses
- The `searchMode` hint and how the service uses it

`docs/concepts/vector-embeddings.md`

- What an embedding is: high-dimensional space, geometric proximity as semantic similarity
- Why model choice matters: `nomic-embed-text` and why it's suited for retrieval tasks
- Vector dimensions: why the index mapping must match the model (768 for nomic-embed-text)
- How embeddings are generated at ingest time and query time via the Ollama API

---

### T10: GraphQL Search API

**Goal:** Expose the full search pipeline via a GraphQL API with GraphiQL for interactive development.

**Deliverables:**

### GraphQL schema (`src/main/resources/graphql/schema.graphqls`)

```graphql
scalar JSON

type Query {
  search(input: SearchInput!): SearchResults!
  searchIndex(resourceType: ResourceType!, input: SearchInput!): SearchResults!
}

input SearchInput {
  query: String!
  filters: SearchFilters
  pagination: Pagination
}

input SearchFilters {
  resourceTypes: [ResourceType!]
  objectType: String
  agency: String
  status: String
  wavelengthBand: String
  journal: String
  nationality: String
  yearFrom: Int
  yearTo: Int
}

input Pagination {
  from: Int = 0
  size: Int = 10
}

enum ResourceType {
  CELESTIAL_OBJECTS
  MISSIONS
  OBSERVATIONS
  ASTRONOMERS
  PUBLICATIONS
}

type SearchResults {
  total: Int!
  hits: [SearchHit!]!
  interpretation: QueryInterpretation
}

type SearchHit {
  id: String!
  resourceType: ResourceType!
  score: Float!
  source: JSON!
}

type QueryInterpretation {
  rewrittenQuery: String
  extractedFilters: JSON
  searchMode: SearchMode!
}

enum SearchMode {
  KEYWORD
  SEMANTIC
  HYBRID
}
```

**`SearchController.java`** (`search/` package) — Spring for GraphQL `@Controller`:

- `@QueryMapping search(SearchInput input)`:
  1. Call `IntentExtractionService.extract(input.query())` → `QueryInterpretation`
  2. Merge extracted filters with explicit `input.filters` (explicit filters take precedence)
  3. Determine search mode: use explicit if set; else use `interpretation.searchMode`
  4. Dispatch to `SearchService.searchHybrid/BM25/KNN` accordingly
  5. Return `SearchResults` including `QueryInterpretation`
- `@QueryMapping searchIndex(ResourceType resourceType, SearchInput input)`:
  - Same pipeline but forces `resourceTypes` filter to `[resourceType]`

### Custom JSON scalar

- Register `JsonScalar` coercing `Map<String, Object>` ↔ GraphQL JSON scalar

### GraphiQL config (`application.yml`)

```yaml
spring:
  graphql:
    graphiql:
      enabled: true
```

**Unit tests:**

- Mock `IntentExtractionService` and `SearchService`
- Verify intent extraction called with raw query
- Verify filter merging (explicit overrides extracted)
- Verify correct `SearchService` method dispatched per `searchMode`
- Verify `searchIndex` forces single-index targeting
- Use Spring GraphQL test client for controller-level tests

**Acceptance:**

- GraphiQL accessible at `localhost:8080/graphiql`
- Full pipeline: query "active NASA missions beyond the asteroid belt" → intent extraction → hybrid search → results with `QueryInterpretation` showing extracted filters
- `searchIndex(CELESTIAL_OBJECTS, ...)` returns only celestial object hits
- `interpretation.extractedFilters` and `searchMode` visible in response
- Pagination (`from`, `size`) works correctly

### Docs

- `docs/api-reference/graphql-schema.md` — full annotated schema, example queries for `search` and `searchIndex`, `SearchMode` explanation, `QueryInterpretation` field reference, curl equivalents
- `docs/architecture/overview.md` — C4-style diagram of full local stack
- `docs/architecture/search-pipeline.md` — sequence diagram of a search request end-to-end
- `docs/architecture/ingest-pipeline.md` — sequence diagram of a bulk ingest request

---

### T11: Integration Test Subproject

**Goal:** Separate Gradle subproject containing tests that run the full stack end-to-end against real Docker Compose infrastructure.

**Deliverables:**

### Gradle subproject (`integration-tests/`)

- `integration-tests/build.gradle.kts`:
  - Dependencies: `spring-boot-starter-test`, `spring-webflux` (for WebTestClient), `jackson-databind`
  - Does not depend on the `service/` project directly — tests via HTTP only
- Root `settings.gradle.kts` updated to `include("service", "integration-tests")`

### Infrastructure assumption

- Tests assume `docker-compose up -d` is already running and `./gradlew :service:bootRun` is up
- Tests do NOT manage Docker or process lifecycle
- If the service is not reachable, tests fail with a clear message: "Integration test infrastructure not running. Start with: docker-compose up -d && ./gradlew :service:bootRun"

### Test base class (`IntegrationTestBase.java`)

- Configures `WebTestClient` pointing at `http://localhost:8080`
- `@BeforeAll` health check: hits `/actuator/health`, fails fast with message if not 200

### Test cases

`IngestIntegrationTest`:

- Bulk ingest a small set of known documents (3–5 per index, defined inline in test)
- Assert 207 response with all successes
- Verify documents exist in OpenSearch via direct HTTP to `localhost:9200`

`BM25SearchIntegrationTest`:

- Ingest documents with known text content
- Execute GraphQL `search` query with `intent-extraction.enabled: false` (bypasses LLM) and known keyword
- Assert expected documents appear in results

`KNNSearchIntegrationTest`:

- Ingest documents with known text
- Execute k-NN-only search (via `searchMode: SEMANTIC` override)
- Assert semantically related documents returned (not just keyword matches)

`HybridSearchIntegrationTest`:

- Verify hybrid results deduplicate correctly (no repeated `id` in results)
- Verify combined scoring: document matching both keyword + semantic ranks higher than one matching only one

`CrossIndexSearchIntegrationTest`:

- Ingest related documents across multiple indexes (e.g., Crab Nebula in celestial_objects + an observation targeting it)
- Execute cross-index search
- Assert hits from multiple `resourceType` values in response

`GraphQLApiIntegrationTest`:

- Full end-to-end GraphQL request via HTTP
- Assert `interpretation` field present in response
- Assert pagination (`from`, `size`) correctly limits results

### Teardown

- `@AfterEach` deletes test-specific documents by ID to keep indexes clean between test runs

### README (`integration-tests/README.md`)

- How to run: `docker-compose up -d && ./gradlew :service:bootRun && ./gradlew :integration-tests:test`
- How to run in CI: same, with Docker Compose as a CI service
- Note on test data: each test manages its own ingest/cleanup

**Acceptance:**

- `./gradlew :integration-tests:test` passes with full stack running
- Tests fail with clear message if infrastructure is not up (not a cryptic connection error)
- Each test is independent: can be run in isolation or in any order

---

### T12: Obsidian Docs Vault Setup

**Goal:** Initialize the `docs/` Obsidian vault with folder structure, config, home page, and placeholder files for every section so future tickets have a place to land their documentation.

**Deliverables:**

### Obsidian config (`docs/.obsidian/`)

- `app.json` — enable Mermaid core plugin, set default theme to dark, disable safe mode
- `core-plugins.json` — enable: graph, backlinks, outgoing-links, tag-pane, page-preview, templates
- No community plugins (zero external dependencies)

### Vault home (`docs/index.md`)

- Project title + one-line description
- Links to all sections: Architecture, Concepts, Guides, API Reference, Deployment
- Links to `docs/assets/nebullama-icon.svg` as a header image (once icon ticket is done)

### Folder structure with placeholder `_index.md` files

```text
docs/
├── .obsidian/
│   ├── app.json
│   └── core-plugins.json
├── index.md
├── assets/                        ← placeholder; icon added in T13
├── architecture/
│   ├── _index.md                  ← "Architecture docs live here"
│   ├── overview.md                ← placeholder (filled in T10)
│   ├── search-pipeline.md         ← placeholder (filled in T10)
│   ├── ingest-pipeline.md         ← placeholder (filled in T10)
│   └── aws-architecture.md        ← placeholder (filled in T16)
├── concepts/
│   ├── _index.md
│   ├── hybrid-search.md           ← placeholder (filled in T8)
│   ├── vector-embeddings.md       ← placeholder (filled in T9)
│   └── intent-extraction.md      ← placeholder (filled in T9)
├── guides/
│   ├── _index.md
│   ├── local-dev-setup.md         ← placeholder (filled in T1/T2)
│   ├── data-ingestion.md          ← placeholder (filled in T3/T5)
│   └── running-searches.md        ← placeholder (filled in T10)
├── api-reference/
│   ├── _index.md
│   ├── graphql-schema.md          ← placeholder (filled in T10)
│   └── ingest-rest-api.md         ← placeholder (filled in T5)
└── deployment/
    ├── _index.md
    └── aws.md                     ← placeholder (filled in T16)
```

Each placeholder file contains: a title heading, a one-line description of what will go here, and a `> 🚧 Work in progress` callout.

**Acceptance:**

- Open `docs/` as an Obsidian vault: no errors, all folders visible in file explorer
- `docs/index.md` renders correctly with working links to section indexes
- Mermaid renders in Obsidian (verify with a sample diagram in `docs/index.md`)

---

### T13: Project Logo & Identity

**Goal:** Add the project mascot icon as a placeholder SVG and wire it into the README and docs home page.

**Deliverables:**

### `docs/assets/nebullama-icon.svg`

- 64×64 pixel art SVG of a llama in a spacesuit
- Elements: spacesuit body (white), gold reflective visor helmet, chest control panel with colored lights, small antenna, nebula wisps in background (purples/pinks/teals), dark space background with scattered stars
- Clean pixel art aesthetic — each "pixel" is a 1×1 `<rect>` element; no paths or gradients
- Scales cleanly as a favicon or README badge
- This is the placeholder; the final Midjourney-generated PNG (`docs/assets/nebullama/nebullama-icon.png`) is added manually later

### `README.md` at repo root

- References `docs/assets/nebullama-icon.svg` as the header image
- Project name + one-line tagline: "Hybrid semantic search over astronomy data — a local-dev learning project for vector search and LLMs"
- Badges: Java 21, Spring Boot 3, OpenSearch 2.x, Ollama
- Sections:
  - **What is this?** — brief description of the project and learning goals
  - **Architecture** — inline the C4 component Mermaid diagram from `docs/architecture/overview.md` (or link to it)
  - **Quick Start** — concise steps: clone → docker-compose up → init.sh → bootRun → GraphiQL
  - **Docs** — link to `docs/index.md` and the Obsidian vault
  - **Stack** — table of tech choices

**Midjourney prompt** (stored as a comment block in `docs/assets/README.md` for reference):
> A cute llama wearing a NASA-style spacesuit with a gold reflective visor helmet, floating in deep space surrounded by a colorful nebula in purples, pinks, and teals. Pixel art style, 64x64 sprite, dark space background with scattered stars, retro game aesthetic, clean crisp pixels, warm gold visor reflection, small antenna on helmet, chest control panel with tiny colored lights. --style raw --ar 1:1 --v 6

**Acceptance:**

- `README.md` renders correctly on GitHub with icon, badges, Mermaid diagram, and Quick Start
- SVG icon displays correctly in a browser and in Obsidian
- `docs/index.md` updated to reference the icon

---

### T14: `guides/running-searches.md`

**Goal:** Write the running-searches guide — the interactive user-facing docs for querying the API via GraphiQL and curl. Not tied to a specific implementation ticket, but needs T10 complete to be accurate.

**Note:** This ticket depends on T10 being done so examples are accurate. It is listed as a minor standalone ticket because it was in the original plan's doc structure but not assigned to T10 (which is already large).

**Deliverables:**

### `docs/guides/running-searches.md`

- How to open GraphiQL at `localhost:8080/graphiql`
- Annotated example queries:
  - Bare string search: `search(input: { query: "Crab Nebula" })`
  - Filtered search: `search(input: { query: "NASA missions", filters: { agency: "NASA", yearFrom: 2000 } })`
  - Single-index search: `searchIndex(resourceType: ASTRONOMERS, input: { query: "pulsar discovery" })`
  - Pagination: `search(input: { query: "...", pagination: { from: 10, size: 5 } })`
- How to read the `QueryInterpretation` response (searchMode, extractedFilters, rewrittenQuery)
- curl equivalents for all GraphQL examples (POST to `/graphql` with JSON body)
- How to disable intent extraction for raw query testing (`search.intent-extraction.enabled: false`)

**Acceptance:**

- A developer who has never used the project can follow the guide to execute their first search
- All example queries are copy-pasteable and work against seeded data

---

### T15: Seed Ingest Convenience Script

**Goal:** Shell script that bulk-ingests all five seed data files in sequence so a new developer can seed the full dataset with one command.

**Deliverables:**

### `scripts/ingest_seed.sh`

- Checks that all 5 seed files exist in `data/`; exits with clear error message if any are missing (run `fetch_seed_data.py` first)
- Checks that the service is running (health check against `localhost:8080/actuator/health`); exits with message if not
- Runs `curl -X POST` for each of the five indexes' bulk endpoints in sequence:

  ```bash
  curl -s -X POST http://localhost:8080/api/v1/ingest/celestial_objects/bulk \
    -H "Content-Type: application/json" \
    -d @data/seed_celestial_objects.json
  ```

- Prints per-index result summary (total docs, success count, failure count) parsed from the 207 response
- Non-zero exit code if any ingest fails

### Update `docs/guides/data-ingestion.md`

- Add section: "Ingest all seed data at once" with `scripts/ingest_seed.sh` usage

**Acceptance:**

- With seed files present and service running: `./scripts/ingest_seed.sh` ingests all 200 documents and prints a summary
- Missing seed files → clear error pointing to `fetch_seed_data.py`
- Service not running → clear error pointing to `./gradlew bootRun`

---

### T16: AWS Deployment Documentation

**Goal:** Write the AWS deployment guide covering how to replace the local Docker stack with AWS managed services (Bedrock, OpenSearch Serverless, ECS Fargate). Documentation only — no code changes.

**Deliverables:**

### `docs/deployment/aws.md`

*Replacing Ollama with Amazon Bedrock:*

- Swap `ollama.base-url` config for Bedrock API endpoint
- Embeddings: use `amazon.titan-embed-text-v2` (1024 dimensions) in place of `nomic-embed-text` (768)
- Intent extraction: use `anthropic.claude-3-haiku` in place of `mistral:7b`
- **Important:** Titan v2 produces 1024-dimension vectors; index mappings must be updated from 768 to 1024
- Spring Boot dependency change: add `software.amazon.awssdk:bedrockruntime`
- IAM: task role needs `bedrock:InvokeModel` permission

*Replacing Docker OpenSearch with OpenSearch Serverless (OSS):*

- Create a vector search collection in OSS
- Update `opensearch.host` to the OSS endpoint
- Auth change: no-auth → SigV4; add `software.amazon.awssdk:opensearchserverless` + request signing interceptor on the `opensearch-java` client
- Index creation via OSS API (same mapping schema, adjusted dimensions)
- **Cost note:** OSS minimum billing is 2 OCUs (~$350/month); not suitable for hobby use — keep Docker OpenSearch for personal projects

*ECS Fargate deployment:*

- Provide a `service/Dockerfile` using `eclipse-temurin:21-jre`
- Push image to ECR
- Task definition: env vars for OSS endpoint and Bedrock region pulled from Secrets Manager
- Health check: ALB target group → `/actuator/health`

*IAM summary:*

- Task role needs: `bedrock:InvokeModel`, `aoss:APIAccessAll` on the OSS collection
- No hardcoded credentials; task role only

### `docs/architecture/aws-architecture.md`

- Narrative description of the AWS architecture
- Mermaid diagram:

  ```mermaid
  graph TD
      ALB["Application Load Balancer"]
      ECS["ECS Fargate\nNebullamaSearch Service"]
      OSS["Amazon OpenSearch Serverless\n(vector search enabled)"]
      Bedrock["Amazon Bedrock\n(Titan Embeddings + Claude Haiku)"]
      ECR["ECR\n(container image)"]
      SM["Secrets Manager\n(OSS endpoint, Bedrock config)"]

      ALB --> ECS
      ECS --> OSS
      ECS --> Bedrock
      ECS --> SM
      ECR --> ECS
  ```

**Acceptance:**

- Both docs render correctly in Obsidian with the Mermaid diagram displaying
- A developer familiar with AWS can follow the guide to deploy without needing to read source code
- Dimension difference (768 local vs 1024 Bedrock) is clearly called out to prevent silent bugs
